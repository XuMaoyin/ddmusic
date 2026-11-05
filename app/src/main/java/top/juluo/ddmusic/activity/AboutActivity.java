package top.juluo.ddmusic.activity;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import top.juluo.ddmusic.R;

/**
 * 关于（二级页）：版本号 + 开发者 + GitHub 地址。
 * 版本号在运行时从 PackageManager 读取，改 build.gradle 的 versionName/versionCode 即可自动同步。
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

        // 点击 GitHub 一行：交给浏览器打开项目主页
        findViewById(R.id.row_github).setOnClickListener(v -> openGithub());
    }

    private void openGithub() {
        String url = getString(R.string.about_github_url);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            // 没有浏览器时至少让用户能看到地址
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }
}
