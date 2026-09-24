package com.pmcn.buildertest;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = new TextView(this);
        title.setText("PMCN Android Builder");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        TextView status = new TextView(this);
        status.setText("\n✅ APK compilado pelo GitHub Actions\n\nSe você está vendo esta tela, nossa fábrica de APKs está funcionando.");
        status.setTextSize(18);
        status.setTextColor(Color.LTGRAY);
        status.setGravity(Gravity.CENTER);

        root.addView(title);
        root.addView(status);
        setContentView(root);
    }
}
