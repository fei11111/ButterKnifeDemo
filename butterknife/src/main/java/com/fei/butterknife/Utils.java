package com.fei.butterknife;

import android.app.Activity;
import android.view.View;

import androidx.annotation.IdRes;

/**
 * @ClassName: Utils
 * @Description: java类作用描述
 * @Author: Fei
 * @CreateDate: 2021-02-10 21:23
 * @UpdateUser: 更新者
 * @UpdateDate: 2021-02-10 21:23
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public class Utils {

    public static <T extends View> T findViewById(Activity target, @IdRes int id) {
        return target.findViewById(id);
    }

}
