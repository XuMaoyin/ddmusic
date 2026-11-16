package top.juluo.ddmusic;

import android.app.Application;

import top.juluo.ddmusic.util.LogKit;
import top.juluo.ddmusic.util.VoiceRegistrar;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 日志初始化放这里：服务层的音频焦点/模式恢复日志在打开播放页之前也能落盘
        LogKit.init(this);
        // 每次进程启动都向小迪补一次注册：车机重装/清数据后白名单会丢
        VoiceRegistrar.register(this);
    }
}
