package top.juluo.ddmusic.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 向小迪（com.byd.autovoice）注册自己为“第三方音乐应用”。
 *
 * <p>小迪的媒体控制白名单不是写死的，而是 {@code ThirdMusicDaoManger} 里的一张表；
 * 第三方应用通过广播 {@code com.byd.action.FUNCTION_UPDATE} 把自己的包名/类型/控制服务写进去，
 * 之后“下一曲 / 暂停 / 继续播放”才会把媒体键派发给本应用。
 *
 * <p>注意：注册入口只接受 {@code MediaUtil.e} 里的包名
 * （酷我、酷狗、喜马拉雅车机版、云听、网易云 IoT 版），普通包名会被静默丢弃。
 */
public final class VoiceRegistrar {

    private static final String TAG = "VoiceRegistrar";
    private static final String ACTION_FUNCTION_UPDATE = "com.byd.action.FUNCTION_UPDATE";
    private static final String PKG_AUTOVOICE = "com.byd.autovoice";

    private VoiceRegistrar() {
    }

    public static void register(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(ACTION_FUNCTION_UPDATE);
            intent.setPackage(PKG_AUTOVOICE);
            intent.putExtra("EXTRA_PACKAGE_NAME", context.getPackageName());
            intent.putExtra("EXTRA_APPLICATION_TYPE", "MUSIC");
            intent.putExtra("ISCONTROL", "true");
            intent.putExtra("EXTRA_SERVICE_NAME",
                    context.getPackageName() + "/top.juluo.ddmusic.service.MusicService");
            context.sendBroadcast(intent);
            Log.d(TAG, "已向小迪注册音乐应用: " + context.getPackageName());
        } catch (Exception e) {
            Log.w(TAG, "向小迪注册失败", e);
        }
    }
}
