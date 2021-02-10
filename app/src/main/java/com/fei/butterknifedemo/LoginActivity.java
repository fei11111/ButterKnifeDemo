package com.fei.butterknifedemo;

import android.os.Bundle;
import android.widget.TextView;

import com.fei.butterknife.BindView;
import com.fei.butterknife.ButterKnife;

import androidx.appcompat.app.AppCompatActivity;

/**
 * @ClassName: LoginActivity
 * @Description: java类作用描述
 * @Author: Fei
 * @CreateDate: 2021-02-10 12:41
 * @UpdateUser: 更新者
 * @UpdateDate: 2021-02-10 12:41
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public class LoginActivity extends AppCompatActivity {

    @BindView(R.id.tv1)
    TextView tv4;

    @BindView(R.id.tv2)
    TextView tv5;

    @BindView(R.id.tv3)
    TextView tv6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

    }
}
