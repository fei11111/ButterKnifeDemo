package com.fei.butterknife;

import android.app.Activity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @ClassName: ButterKnife
 * @Description: 工具类
 * @Author: Fei
 * @CreateDate: 2021-02-10 11:52
 * @UpdateUser: 更新者
 * @UpdateDate: 2021-02-10 11:52
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public class ButterKnife {

    /**
     * 绑定activity，其实就是创建xxxActivity_ViewBinding对象
     * @param activity
     * @return
     */
    public static UnBinder bind(Activity activity) {
        String name = activity.getClass().getName();
        try {
            Class<? extends UnBinder> clazz = (Class<? extends UnBinder>) Class.forName(name + "_ViewBinding");
            if (clazz != null) {
                Constructor<? extends UnBinder> constructor = clazz.getDeclaredConstructor(activity.getClass());
                constructor.setAccessible(true);
                return constructor.newInstance(activity);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return UnBinder.EMPTY;
    }

}
