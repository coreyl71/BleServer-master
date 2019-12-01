package cc.noharry.bleserver;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import cc.noharry.bleserver.adapter.MsgShowAdapter;
import cc.noharry.bleserver.bean.MsgBean;
import cc.noharry.bleserver.ble.BLEAdmin;
import cc.noharry.bleserver.ble.IMsgReceive;
import cc.noharry.bleserver.ble.OnBTOpenStateListener;
import cc.noharry.bleserver.utils.L;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements OnClickListener, IMsgReceive {

    private OnBTOpenStateListener btOpenStateListener = null;

    private Button mBtInit;
    // 发送消息按钮
    private Button btn_send_msg;
    // 发送消息输入框
    private EditText et_msg_to_send;
    // 最终拼接消息显示
    private TextView tv_final_msg_show;
    // 消息接收显示列表
    private RecyclerView rcv_msg_show;
    // 布局管理器
    private LinearLayoutManager llm;
    // 列表适配器
    private MsgShowAdapter msgShowAdapter;
    /**
     * 消息列表
     */
    private List<MsgBean> msgList;
    /**
     * 用来保存数据分包的集合
     */
    private List<byte[]> contentBytes;


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
        setOnClickListener();

        if (null != msgList) {
            msgList.clear();
        } else {
            msgList = new ArrayList<>();
        }
        if (null == contentBytes) {
            contentBytes = new ArrayList<>();
        } else {
            contentBytes.clear();
        }


        // 设置适配器
        setAdapter();

    }


    /**
     * 获取消息列表数据
     */
    @Override
    public void onReceiveMsg(MsgBean msgBean, byte[] contentByte) {

//        this.msgList.add(msgBean);
//
//        if (null != msgList && msgList.size() != 0) {
//            setAdapter();
//        }

        this.contentBytes.add(contentByte);

    }

    /**
     * 获取消息完成
     */
    @Override
    public void onReceiveMsgComplete() {
        L.i("onReceiveMsgComplete");
        if (null != contentBytes && contentBytes.size() != 0) {
            // 计算总字节长度
            int contentByteLength = contentBytes.size() * 14;
            // 待拼接数组，最终用来转换字符串显示
            byte[] contentBytesConcat = new byte[contentByteLength];
            for (int i = 0; i < contentBytes.size(); i++) {
                System.arraycopy(contentBytes.get(i), 0, contentBytesConcat, i * 14, 14);
            }
            // 转成字符串
            String finalStr = new String(contentBytesConcat);
            // 显示
            tv_final_msg_show.setText(finalStr);
        }

    }

    /**
     * 消息回滚、重发
     */
    @Override
    public void onMsgResend() {

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

    // 是否正在写入数据
    private boolean isWritingEntity;
    // 当前是否为自动写入模式
    private boolean isAutoWriteMode = false;
    // 最后一包是否自动补零
    private final boolean lastPackComplete = false;
    // 单个数据包大小
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

    /**
     * 设置列表适配器
     */
    private void setAdapter() {

        if (null == msgShowAdapter) {

            msgShowAdapter = new MsgShowAdapter(MainActivity.this, msgList);
            rcv_msg_show.setAdapter(msgShowAdapter);

        } else {
            // 刷新适配器
            msgShowAdapter.update(msgList);
        }

    }

    private void setOnClickListener() {
        mBtInit.setOnClickListener(this);
        btn_send_msg.setOnClickListener(this);
    }

    private void initView() {
        mBtInit = findViewById(R.id.bt_init);
        btn_send_msg = findViewById(R.id.btn_send_msg);
        et_msg_to_send = findViewById(R.id.et_msg_to_send);
        tv_final_msg_show = findViewById(R.id.tv_final_msg_show);
        rcv_msg_show = findViewById(R.id.rcv_msg_show);
        llm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        rcv_msg_show.setLayoutManager(llm);
    }


}
