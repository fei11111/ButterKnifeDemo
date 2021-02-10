package com.fei.butterknife;

/**
 * @ClassName: UnBinder
 * @Description: java类作用描述
 * @Author: Fei
 * @CreateDate: 2021-02-10 11:52
 * @UpdateUser: 更新者
 * @UpdateDate: 2021-02-10 11:52
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public interface UnBinder {

    void unBind();

    UnBinder EMPTY = new UnBinder() {
        @Override
        public void unBind() {

        }
    };

}
