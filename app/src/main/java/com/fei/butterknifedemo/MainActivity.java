package com.fei.butterknifedemo;

import android.os.Bundle;
import android.widget.TextView;

import com.fei.butterknife.BindView;
import com.fei.butterknife.ButterKnife;
import com.fei.butterknife.UnBinder;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.tv1)
    TextView tv1;
    @BindView(R.id.tv2)
    TextView tv2;

    @BindView(R.id.tv3)
    TextView tv3;

    private UnBinder unBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        unBinder = ButterKnife.bind(this);

        tv1.setText("tv1");
        tv2.setText("tv2");
        tv3.setText("tv3");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unBinder.unBind();

    }
}