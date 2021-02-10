package com.fei.butterknife;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@AutoService(Processor.class)
public class ButterKnifeProcessor extends AbstractProcessor {

    static final String VIEW_TYPE = "android.view.View";
    static final ClassName UTILS = ClassName.get("com.fei.butterknife", "Utils");
    static final ClassName UNBINDER = ClassName.get("com.fei.butterknife", "UnBinder");
    static final ClassName UI_THREAD =
            ClassName.get("androidx.annotation", "UiThread");

    private Filer mFiler;
    private Elements mElementUtils;
    private @Nullable
    Trees mTrees;

    private final RScanner rScanner = new RScanner();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mElementUtils = processingEnv.getElementUtils();
        mFiler = processingEnv.getFiler();
        try {
            mTrees = Trees.instance(processingEnv);
        } catch (IllegalArgumentException ignored) {
            try {
                // Get original ProcessingEnvironment from Gradle-wrapped one or KAPT-wrapped one.
                for (Field field : processingEnv.getClass().getDeclaredFields()) {
                    if (field.getName().equals("delegate") || field.getName().equals("processingEnv")) {
                        field.setAccessible(true);
                        ProcessingEnvironment javacEnv = (ProcessingEnvironment) field.get(processingEnv);
                        mTrees = Trees.instance(javacEnv);
                        break;
                    }
                }
            } catch (Throwable ignored2) {
            }
        }
    }

    /**
     * 用来指定支持的 SourceVersion
     *
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 用来指定支持的 AnnotationTypes
     *
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (Class<? extends Annotation> clazz : getSupportedAnnotation()) {
            types.add(clazz.getName());
        }
        return types;
    }

    /**
     * 支持的annotation
     *
     * @return
     */
    public Set<Class<? extends Annotation>> getSupportedAnnotation() {
        Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
        //这里还可以添加更多自定义的annotation
        annotations.add(BindView.class);
        return annotations;
    }

    /**
     * 判断该注解下的属性、方法等是否满足规则，例如@BindView属性不能是private或者static
     *
     * @param annotationClass
     * @param targetThing
     * @param element
     * @return
     */
    private boolean isInaccessibleViaGeneratedCode(Class<? extends Annotation> annotationClass,
                                                   String targetThing, Element element) {
        boolean hasError = false;
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        // Verify field or method modifiers.
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(PRIVATE) || modifiers.contains(STATIC)) {
            error(element, "@%s %s must not be private or static. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        // Verify containing type.
        if (enclosingElement.getKind() != CLASS) {
            error(enclosingElement, "@%s %s may only be contained in classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        // Verify containing class visibility is not private.
        if (enclosingElement.getModifiers().contains(PRIVATE)) {
            error(enclosingElement, "@%s %s may not be contained in private classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            hasError = true;
        }

        return hasError;
    }


    /**
     * 判断包名是否正确，排除系统包名
     *
     * @param annotationClass
     * @param element
     * @return
     */
    private boolean isBindingInWrongPackage(Class<? extends Annotation> annotationClass,
                                            Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith("android.")) {
            error(element, "@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }
        if (qualifiedName.startsWith("java.")) {
            error(element, "@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }

        return false;
    }

    /**
     * 判断是否是接口
     *
     * @param typeMirror
     * @return
     */
    private boolean isInterface(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType
                && ((DeclaredType) typeMirror).asElement().getKind() == INTERFACE;
    }

    static boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
        if (isTypeEqual(typeMirror, otherType)) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(',');
                }
                typeString.append('?');
            }
            typeString.append('>');
            if (typeString.toString().equals(otherType)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, otherType)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 类型比较
     *
     * @param typeMirror
     * @param otherType
     * @return
     */
    private static boolean isTypeEqual(TypeMirror typeMirror, String otherType) {
        return otherType.equals(typeMirror.toString());
    }

    /**
     * 处理注解
     *
     * @param annotations
     * @param roundEnv
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //类、类下的属性
        Map<TypeElement, List<Element>> elementsMap = findAndParseTargets(roundEnv);
        //遍历map,这里只处理BindView注解
        for (Map.Entry<TypeElement, List<Element>> entry : elementsMap.entrySet()) {
            //enclosingElement为类 xxxActivity
            TypeElement enclosingElement = entry.getKey();
            //创建类名
            String clazzName = enclosingElement.getSimpleName() + "_ViewBinding";
            //所有该类下的BindView注解的属性
            List<Element> values = entry.getValue();
            //创建类 public final class xxxActivity_ViewBind implements Unbind
            TypeSpec.Builder typeBuilder = createType(clazzName);
            //Activity作为参数类型
            TypeName parameter = TypeName.get(enclosingElement.asType());
            //添加属性 private xxxActivity target;
            FieldSpec.Builder targetField = FieldSpec.builder(parameter, "target", PRIVATE);
            //创建构造函数 public xxActivity_ViewBind(Activity target)
            MethodSpec.Builder constructorMethod = createConstructor(parameter);
            //创建unbind方法
            MethodSpec.Builder bindingUnbindMethod = createBindingUnbindMethod();
            bindingUnbindMethod.addStatement("if (this.target == null) throw new IllegalStateException(\"Bindings already cleared.\")");
            //构造函数添加语句
            constructorMethod.addStatement("this.target = target");
            for (Element element : values) {
                //添加findViewById
                String fieldName = element.getSimpleName().toString();
                int id = element.getAnnotation(BindView.class).value();
                Id resourceId = elementToId(element, BindView.class, id);
                constructorMethod.addStatement("this.target.$L = $T.findViewById(target,$L)", fieldName,
                        UTILS, resourceId.code);
                //这里方式也是可以
//                constructorMethod.addStatement("this.target.$L = target.findViewById($L)",element.getSimpleName().toString(),resourceId.code);
                //unbind方法添加语句
                bindingUnbindMethod.addStatement("this.target.$L = null", fieldName);
            }
            //unbind方法添加语句
            bindingUnbindMethod.addStatement("this.target = null");
            //添加属性
            typeBuilder.addField(targetField.build());
            //添加构造函数
            typeBuilder.addMethod(constructorMethod.build());
            //添加unbind方法
            typeBuilder.addMethod(bindingUnbindMethod.build());
            try {
                //获取包名
                String packageName = mElementUtils.getPackageOf(enclosingElement).getQualifiedName().toString();
                //写入文件
                JavaFile.builder(packageName, typeBuilder.build()).build().writeTo(mFiler);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return false;
    }

    /**
     * 创建类
     */
    private TypeSpec.Builder createType(String clazzName) {
       return TypeSpec.classBuilder(clazzName).
                addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(UNBINDER);
    }

    /**
     * 创建构造函数
     * @return
     */
    private MethodSpec.Builder createConstructor(TypeName parameter) {
       return MethodSpec.constructorBuilder()
                .addParameter(parameter, "target", Modifier.FINAL)
                .addAnnotation(UI_THREAD)
                .addModifiers(PUBLIC);
    }

    /**
     * 创建unbind方法
     *
     * @return
     */
    private MethodSpec.Builder createBindingUnbindMethod() {
        return MethodSpec.methodBuilder("unBind")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class);
    }

    /**
     * 获取所有指定注解并分析
     *
     * @param env
     * @return
     */
    private Map<TypeElement, List<Element>> findAndParseTargets(RoundEnvironment env) {
        //类、类下的属性
        Map<TypeElement, List<Element>> elementsMap = new LinkedHashMap<>();
        //获取所有添加了BindView注解的所有元素
        Set<? extends Element> bindViewElements = env.getElementsAnnotatedWith(BindView.class);
        //解析并判断元素
        parseBindView(env, elementsMap, bindViewElements);
        return elementsMap;
    }

    /**
     * 将id转成资源id
     *
     * @param element
     * @param annotation
     * @param value
     * @return
     */
    private Id elementToId(Element element, Class<? extends Annotation> annotation, int value) {
        JCTree tree = (JCTree) mTrees.getTree(element, getMirror(element, annotation));
        if (tree != null) { // tree can be null if the references are compiled types and not source
            rScanner.reset();
            tree.accept(rScanner);
            if (!rScanner.resourceIds.isEmpty()) {
                return rScanner.resourceIds.values().iterator().next();
            }
        }
        return new Id(value);
    }

    private static @Nullable
    AnnotationMirror getMirror(Element element,
                               Class<? extends Annotation> annotation) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotation.getCanonicalName())) {
                return annotationMirror;
            }
        }
        return null;
    }

    private void parseBindView(RoundEnvironment env, Map<TypeElement, List<Element>> elementsMap, Set<? extends Element> bindViewElements) {
        //类对应多个属性，遍历，将属性和类一一对应
        for (Element element : bindViewElements) {
            //获取类
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            //判断是否private或static
            boolean hasError = isInaccessibleViaGeneratedCode(BindView.class, "fields", element)
                    || isBindingInWrongPackage(BindView.class, element);
            //判断属性是不是继承View
            TypeMirror elementType = element.asType();
            if (elementType.getKind() == TypeKind.TYPEVAR) {
                TypeVariable typeVariable = (TypeVariable) elementType;
                elementType = typeVariable.getUpperBound();
            }
            Name qualifiedName = enclosingElement.getQualifiedName();
            Name simpleName = element.getSimpleName();
            if (!isSubtypeOfType(elementType, VIEW_TYPE) && !isInterface(elementType)) {
                if (elementType.getKind() == TypeKind.ERROR) {
                    note(element, "@%s field with unresolved type (%s) "
                                    + "must elsewhere be generated as a View or interface. (%s.%s)",
                            BindView.class.getSimpleName(), elementType, qualifiedName, simpleName);
                } else {
                    error(element, "@%s fields must extend from View or be an interface. (%s.%s)",
                            BindView.class.getSimpleName(), qualifiedName, simpleName);
                    hasError = true;
                }
            }
            if (hasError) return;
            //添加进map
            List<Element> elements = elementsMap.get(enclosingElement);
            if (elements == null) {
                elements = new ArrayList<>();
                elementsMap.put(enclosingElement, elements);
            }
            elements.add(element);
        }
    }


    //打印
    private void error(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.ERROR, element, message, args);
    }

    //打印
    private void note(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.NOTE, element, message, args);
    }

    //打印
    private void printMessage(Diagnostic.Kind kind, Element element, String message, Object[] args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }

        processingEnv.getMessager().printMessage(kind, message, element);
    }

    private static class RScanner extends TreeScanner {
        Map<Integer, Id> resourceIds = new LinkedHashMap<>();

        @Override
        public void visitIdent(JCTree.JCIdent jcIdent) {
            super.visitIdent(jcIdent);
            Symbol symbol = jcIdent.sym;
            if (symbol.type instanceof Type.JCPrimitiveType) {
                Id id = parseId(symbol);
                if (id != null) {
                    resourceIds.put(id.value, id);
                }
            }
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess jcFieldAccess) {
            Symbol symbol = jcFieldAccess.sym;
            Id id = parseId(symbol);
            if (id != null) {
                resourceIds.put(id.value, id);
            }
        }

        @Nullable
        private Id parseId(Symbol symbol) {
            Id id = null;
            if (symbol.getEnclosingElement() != null
                    && symbol.getEnclosingElement().getEnclosingElement() != null
                    && symbol.getEnclosingElement().getEnclosingElement().enclClass() != null) {
                try {
                    int value = (Integer) requireNonNull(((Symbol.VarSymbol) symbol).getConstantValue());
                    id = new Id(value, symbol);
                } catch (Exception ignored) {
                }
            }
            return id;
        }

        @Override
        public void visitLiteral(JCTree.JCLiteral jcLiteral) {
            try {
                int value = (Integer) jcLiteral.value;
                resourceIds.put(value, new Id(value));
            } catch (Exception ignored) {
            }
        }

        void reset() {
            resourceIds.clear();
        }
    }
}