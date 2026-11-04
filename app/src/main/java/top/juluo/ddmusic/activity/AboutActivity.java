package top.juluo.ddmusic.activity;

import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import top.juluo.ddmusic.R;

/**
 * 关于（二级页）：版本号 + 开发者。
 * GitHub 地址留了位置，项目上传后把 about_github_pending 换成真实链接即可。
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView versionView = findViewById(R.id.about_version);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionView.setText(getString(R.string.about_version,
                    info.versionName, info.versionCode));
        } catch (Exception e) {
            versionView.setText("");
        }
    }
}
