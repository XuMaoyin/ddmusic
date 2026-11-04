package top.juluo.ddmusic.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import top.juluo.ddmusic.receiver.KuwoVoiceReceiver;
import top.juluo.ddmusic.service.MusicService;

/**
 * 小迪语音指令的中转页：不绘制任何界面，处理完立即自行结束。
 *
 * <p>为什么不让 MainActivity 直接承接：MainActivity 是 {@code singleTask}，
 * 被语音拉起时系统会把它之上的播放页清掉，用户看到的就是"从播放页跳回列表"。
 * 用独立 Activity 承接则可以叠加在任意界面之上，处理完 {@code finish()} 就回到原界面，
 * 返回栈完全不动。
 *
 * <p>冷启动（进程原本不存在）时这条指令就是用户的全部意图，所以额外打开播放页，
 * 保持"说完话直接看到播放界面"的体验；而应用已在前台/后台运行时只播放、不切换界面。
 */
public class VoiceRelayActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        // 播放服务存活即视为"应用已在运行"：MainActivity/PlayerActivity 绑定过服务，
        // 且 MusicService 是 START_STICKY，进程活着它就活着
        boolean warm = MusicService.getInstance() != null;
        boolean hasSong = hasConcreteSongQuery(intent);

        // 交给既有的语音接收器执行播放（与广播通道复用同一套搜索/播放逻辑）
        try {
            new KuwoVoiceReceiver().onReceive(this, intent);
        } catch (Exception ignored) {
        }

        if (!warm && hasSong) {
            // 冷启动：播放页是用户想看到的界面。
            // 中转页在独立任务里，这里必须用 NEW_TASK 把播放页放回应用主任务栈。
            try {
                Intent player = new Intent(this, PlayerActivity.class);
                player.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(player);
            } catch (Exception ignored) {
            }
        }
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        // 不要转场动画，避免透明页进出时"闪一下"
        overridePendingTransition(0, 0);
    }

    /** 与 KuwoVoiceReceiver 提取歌名所用候选字段保持一致 */
    private boolean hasConcreteSongQuery(@Nullable Intent intent) {
        Bundle extras = intent == null ? null : intent.getExtras();
        if (extras == null) return false;
        String[] keys = {"name", "songname", "songName", "song", "keyword", "query",
                "title", "value", "content", "search", "word", "key", "searchKey"};
        for (String k : keys) {
            Object v = extras.get(k);
            if (v != null && !v.toString().trim().isEmpty()) return true;
        }
        return false;
    }
}
