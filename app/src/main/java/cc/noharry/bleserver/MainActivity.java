package cc.noharry.bleserver;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import cc.noharry.bleserver.ble.BLEAdmin;
import cc.noharry.bleserver.ble.OnBTOpenStateListener;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements OnClickListener {

    private OnBTOpenStateListener btOpenStateListener = null;

    private Button mBtInit;
    // 发送消息按钮
    private Button btn_send_msg;
    // 发送消息输入框
    private EditText et_msg_to_send;
    private UUID UUID_SERVER = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb");
    private TextView mTv_times;
    public AtomicInteger times = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化蓝牙开启的监听接口
        btOpenStateListener = new OnBTOpenStateListener() {
            @Override
            public void onBTOpen() {
                L.i("开启蓝牙");
                // 蓝牙开启成功后，回调中初始化 GATT
                initGatt();
            }
        };

        // 找控件
        initView();
        // 点击事件监听
        initEvent();

    }

    private void initEvent() {
        mBtInit.setOnClickListener(this);
        btn_send_msg.setOnClickListener(this);
    }

    private void initView() {
        mBtInit = findViewById(R.id.bt_init);
        btn_send_msg = findViewById(R.id.btn_send_msg);
        et_msg_to_send = findViewById(R.id.et_msg_to_send);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.bt_init:

                // 开启蓝牙
                openBT();
                break;

            case R.id.btn_send_msg:

                String data = et_msg_to_send.getText().toString().trim();
                if (TextUtils.isEmpty(data)) {
                    Toast.makeText(MainActivity.this, "请输入发送内容", Toast.LENGTH_SHORT).show();
                    break;
                }

                // 字符串转换成 Byte 数组
                byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
                // 数据分包
                subpackageByte(dataBytes);
                break;

            default:
                break;

        }

    }

    private boolean isWritingEntity;
    // 当前是否为自动写入模式
    private boolean isAutoWriteMode = false;
    // 最后一包是否自动补零
    private final boolean lastPackComplete = false;
    private int packLength = 20;
    private final Object lock = new Object();

    private HashSet<Integer> resIdSets = new HashSet<>();

    /**
     * 数据分包
     *
     * @param data 数据源
     */
    private void subpackageByte(byte[] data) {

        isWritingEntity = true;
        int index = 0;
        int length = data.length;
        int availableLength = length;

        while (index < length) {

            if (!isWritingEntity) {
                L.e("写入取消");
            }

            // 每包大小为 20
            int onePackLength = packLength;
            //最后一包不足数据字节不会自动补零
            if (!lastPackComplete) {
                onePackLength = (availableLength >= packLength ? packLength : availableLength);
            }

            byte[] txBuffer = new byte[onePackLength];

            txBuffer[0] = 0x00;
            for (int i = 0; i < onePackLength; i++) {
                if (index < length) {
                    txBuffer[i] = data[index++];
                }
            }

            availableLength -= onePackLength;

            // 单个数据包发送
            boolean result = BLEAdmin.getInstance(this).sendMessage(txBuffer);

            if (!result) {
//                if(mBleEntityLisenter != null) {
//                    mBleEntityLisenter.onWriteFailed();
                isWritingEntity = false;
                isAutoWriteMode = false;
//                    return false;
//                }
            } else {
//                if (mBleEntityLisenter != null) {
                double progress = new BigDecimal((float) index / length).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
//                    mBleEntityLisenter.onWriteProgress(progress);
//                }
            }

//            if (autoWriteMode) {
//                synchronized (lock) {
//                    try {
////                        lock.wait(500);
//                        lock.wait();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            } else {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            }
        }

        L.e("写入完成");

    }

    /**
     * 开启蓝牙
     */
    private void openBT() {
        BLEAdmin.getInstance(this).openBT(btOpenStateListener);
    }

    /**
     * 初始化 GATT
     */
    private void initGatt() {
        BLEAdmin.getInstance(this).initGATTServer();
    }

}
