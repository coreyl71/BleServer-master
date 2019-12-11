package cc.noharry.bleserver;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import cc.noharry.bleserver.ContentValue.BFrameConst;
import cc.noharry.bleserver.ContentValue.ContantValue;
import cc.noharry.bleserver.bean.MsgBean;
import cc.noharry.bleserver.ble.BLEAdmin;
import cc.noharry.bleserver.ble.IBTOpenStateChange;
import cc.noharry.bleserver.ble.IMsgReceive;
import cc.noharry.bleserver.utils.L;
import cc.noharry.bleserver.utils.LogUtil;
import cc.noharry.bleserver.utils.MySP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements OnClickListener, IMsgReceive {

    private IBTOpenStateChange btOpenStateListener = null;

    private Button mBtInit;
    // 发送消息按钮
    private Button btn_send_msg;
    // 发送消息输入框
    private EditText et_msg_to_send;
    // 最终拼接消息显示
    private TextView tv_final_msg_show;
    // 消息接收显示列表
//    private RecyclerView rcv_msg_show;
    // 布局管理器
//    private LinearLayoutManager llm;
    // 列表适配器
//    private MsgShowAdapter msgShowAdapter;
    /**
     * 消息列表
     */
    private List<MsgBean> msgList;
    /**
     * 用来保存数据分包的集合
     */
    private List<byte[]> contentBytes;

    private Handler mHandler;

    // 请求连接主机的名称和 MAC 地址
    private LinearLayout ll_ble_connect_info;
    private TextView tv_ble_device_name, tv_ble_device_address;

    // 本地保存的主设备 Token
    private String remoteTokenSP;
    // 接收到的主设备 Token
    private String remoteTokenReceived;
    // 是否需要用户授权，主要用于做按键拦截的判断
    private boolean needUserAuth;


    private UUID UUID_SERVER = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb");
    private TextView mTv_times;
    public AtomicInteger times = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化蓝牙开启的监听接口
        btOpenStateListener = new IBTOpenStateChange() {
            @Override
            public void onBTOpen() {
                L.i("开启蓝牙");
                // 蓝牙开启成功后，回调中初始化 GATT
                initGatt();
            }
        };

        mHandler = new Handler();

        // 找控件
        initView();

        // 点击事件监听
        setOnClickListener();

//        if (null != msgList) {
//            msgList.clear();
//        } else {
//            msgList = new ArrayList<>();
//        }
        if (null == contentBytes) {
            contentBytes = new ArrayList<>();
        } else {
            contentBytes.clear();
        }
//
//
//        // 设置适配器
//        setAdapter();

        // 不需要用户授权
        needUserAuth = false;
        // 获取本地保存的主设备 Token
        getRemoteDeviceToken();

        // 开启蓝牙
        openBT();

    }

    /**
     * 获取本地保存的主设备TOKEN，用来做主设备连接时的判断，是否需要弹框
     */
    private void getRemoteDeviceToken() {

        remoteTokenSP = MySP.getStringShare(this, ContantValue.SP_BLE_REMOTE_INFO, ContantValue.SP_BLE_REMOTE_TOKEN, "");

    }


    /**
     * 收到请求连接的消息
     */
    @Override
    public void onApplyConnection(BluetoothDevice device) {

        L.i("conn---deviceName = " + device.getName());
        L.i("conn---deviceAddress = " + device.getAddress());

        // 显示用户确认连接 BLE 的弹框
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ll_ble_connect_info.setVisibility(View.VISIBLE);
                if (!TextUtils.isEmpty(device.getName())) {
                    tv_ble_device_name.setVisibility(View.VISIBLE);
                    tv_ble_device_name.setText(device.getName());
                } else {
                    tv_ble_device_name.setVisibility(View.GONE);
                }
                if (!TextUtils.isEmpty(device.getAddress())) {
                    tv_ble_device_address.setVisibility(View.VISIBLE);
                    tv_ble_device_address.setText(device.getAddress());
                } else {
                    tv_ble_device_address.setVisibility(View.GONE);
                }
            }
        });


    }


    /**
     * 获取消息列表数据
     */
    @Override
    public void onReceiveMsg(MsgBean msgBean, byte[] contentByte) {

        isReceiveTokenComplete = false;
//        this.msgList.add(msgBean);
//
//        if (null != msgList && msgList.size() != 0) {
//            setAdapter();
//        }

        this.contentBytes.add(contentByte);

        // TODO: 2019/12/11 之后要判断是接收哪种消息，做相应处理
        if (contentByte[contentByte.length - 1] == (byte) 0x00) {
            if (msgBean.getMsgId() == BFrameConst.START_MSG_ID_UNIQUE) {
                receiveTokenCompleted();
            } else if (msgBean.getMsgId() == BFrameConst.START_MSG_ID_CONTENT) {
                receiveMsgComplete();
            }
        }

    }

    private boolean isReceiveTokenComplete = false;

    /**
     * 获取TOKEN完成
     * 与本地 TOKEN 比对，一致则同意连接，否则弹框需用户同意
     */
    @Override
    public void onReceiveTokenComplete() {

        receiveTokenCompleted();

    }

    private void receiveTokenCompleted() {
        isReceiveTokenComplete = true;

        L.i("onReceiveTokenComplete");
        if (null != contentBytes && contentBytes.size() != 0) {

            // 计算总字节长度
            int contentByteLength = contentBytes.size() * 14;
            // 待拼接数组，最终用来转换字符串显示
            byte[] contentBytesConcat = new byte[contentByteLength];
            for (int i = 0; i < contentBytes.size(); i++) {
                System.arraycopy(contentBytes.get(i), 0, contentBytesConcat, i * 14, 14);
            }
            // 转成字符串
            remoteTokenReceived = new String(contentBytesConcat);

            L.e("onReceiveTokenComplete---remoteTokenReceived = " + remoteTokenReceived);
            L.e("onReceiveTokenComplete---remoteTokenSP = " + remoteTokenSP);
            // 将收到的主设备 Token 和本地的对比，如果不一致则需要用户授权
            if (!TextUtils.isEmpty(remoteTokenReceived) && !TextUtils.isEmpty(remoteTokenSP)) {

                boolean isMatch = TextUtils.equals(remoteTokenReceived, remoteTokenSP);
                L.e("onReceiveTokenComplete---remoteTokenSP = " + isMatch);
                // 不匹配，则需要用户授权
                if (!isMatch) {

                    // 需要用户授权
                    needUserAuth = true;
                    // TODO: 2019/12/10 弹框需要用户授权，同意连接才刷新本地 SP
                    ll_ble_connect_info.setVisibility(View.VISIBLE);
                    tv_ble_device_address.setVisibility(View.GONE);

//                    if (!TextUtils.isEmpty(remoteTokenStr)) {
//                        tv_ble_device_address.setVisibility(View.VISIBLE);
//                        tv_ble_device_address.setText(remoteTokenStr);
//                    } else {
//                        tv_ble_device_address.setVisibility(View.GONE);
//                    }

                    // 刷新 SP 中存储的主设备 Token
                    updateRemoteTokenSP(remoteTokenReceived);

                } else {

                    // 不需要用户授权，直接连接
                    needUserAuth = false;
                    // TODO: 2019/12/10 弹框需要用户授权，同意连接才刷新本地 SP
                    ll_ble_connect_info.setVisibility(View.GONE);

                }

            }

        }
    }


    /**
     * 获取消息完成
     */
    @Override
    public void onReceiveMsgComplete() {
        receiveMsgComplete();
    }

    private void receiveMsgComplete() {
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


                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        getData();
                    }
                });


//                String data = et_msg_to_send.getText().toString().trim();
//                String data = getData().trim();
//                if (TextUtils.isEmpty(data)) {
//                    Toast.makeText(MainActivity.this, "请输入发送内容", Toast.LENGTH_SHORT).show();
//                    break;
//                }
//
//                L.i("data.length = " + data.length());
//                // 字符串转换成 Byte 数组
//                byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
//                // 数据分包
//                subpackageByte(dataBytes);
                break;

            default:
                break;

        }

    }

    //获取assets文件夹下aaa.text的json字符串
    private void getData() {

        String resultStr = null;

        try {
            InputStreamReader reader = new InputStreamReader(getAssets().open("aaa.txt"));
            BufferedReader bufferedReader = new BufferedReader(reader);
            StringBuffer buffer = new StringBuffer("");
            String str;
            try {
                while ((str = bufferedReader.readLine()) != null) {
                    buffer.append(str);
                }
                resultStr = buffer.toString();

                // 字符串转换成 Byte 数组
                byte[] dataBytes = resultStr.getBytes(StandardCharsets.UTF_8);
                // 数据分包
                subpackageByte(dataBytes, BFrameConst.START_MSG_ID_CONTENT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

//        return resultStr;
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
    private void subpackageByte(byte[] data, int msg_id) {

        // 连接间隔时间修改
//        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
//            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
//        }

        isWritingEntity = true;
        // 数据源数组的指针
        int index = 0;
        // 数据总长度
        int dataLength = data.length;
        // 待传数据有效长度，最后一个包是否需要补零
        int availableLength = dataLength;
        // 待发送数据包的个数
//        int packageCount = ((dataLength % 14 == 0) ? (dataLength / 14) : (dataLength / 14 + 1));
        int packageCount = ((dataLength % 18 == 0) ? (dataLength / 18) : (dataLength / 18 + 1));

        // 重试次数
        int retryCount = 0;
        // 是否消息开始第一个包，内含消息分包的个数
        boolean isMsgStart = true;

        while (index < dataLength) {

            // 未就绪，可能没收到返回，或未成功写入
            if (!isWritingEntity) {

                // 小于五次则等待，多于五次（250ms）则重发
                if (retryCount < 5) {

                    L.e("等待分包");
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    retryCount++;
                    continue;

                } else {

                    // 重置次数，方便下次阻塞的时候计数
                    retryCount = 0;

                }

            }
            L.e("开始分包");
            // 开始分包，状态置为未就绪状态
            isWritingEntity = false;

            // 每包数据内容大小为 14
//            int onePackLength = packLength - 6;
            int onePackLength = packLength - 4;
            // 最后一包不足长度不会自动补零
            if (!lastPackComplete) {
//                onePackLength = (availableLength >= (packLength - 6) ? (packLength - 6) : availableLength);
                onePackLength = (availableLength >= (packLength - 4) ? (packLength - 4) : availableLength);
            }

            // 实例化一个数据分包，长度为 20
//            byte[] txBuffer = new byte[onePackLength];
            byte[] txBuffer = new byte[packLength];

            // 数据包头 (byte)0xFF
//            txBuffer[0] = BFrameConst.FRAME_HEAD;
            // 数据包尾 (byte)0x00;
//            txBuffer[19] = BFrameConst.FRAME_END;

            byte[] msgIdByte;
            byte[] packageCountByte;
            byte[] msgStartIdByte;
            if (isMsgStart) {

                // 数据包 [1]-[4] 为 msgId，起始位的 msgId 为 1，代表只发送头部信息，包含消息分包的个数
                msgIdByte = int2byte(BFrameConst.START_MSG_ID_START);
                packageCountByte = int2byte(packageCount);
                msgStartIdByte = int2byte(msg_id);


                /**
                 * 首包数组拷贝
                 * 原数组
                 * 元数据的起始位置
                 * 目标数组
                 * 目标数组的开始起始位置
                 * 要 copy 的数组的长度
                 */
//                System.arraycopy(msgIdByte, 0, txBuffer, 1, BFrameConst.MESSAGE_ID_LENGTH);
//                System.arraycopy(packageCountByte, 0, txBuffer, 5, BFrameConst.MESSAGE_ID_LENGTH);
//                System.arraycopy(msgStartIdByte, 0, txBuffer, 9, BFrameConst.MESSAGE_ID_LENGTH);
                System.arraycopy(msgIdByte, 0, txBuffer, 0, BFrameConst.MESSAGE_ID_LENGTH);
                System.arraycopy(packageCountByte, 0, txBuffer, 4, BFrameConst.MESSAGE_ID_LENGTH);
                System.arraycopy(msgStartIdByte, 0, txBuffer, 8, BFrameConst.MESSAGE_ID_LENGTH);

                // 单个数据包发送
//                boolean result = write(txBuffer);
                boolean result = BLEAdmin.getInstance(this).sendMessage(txBuffer);

//                if (!result) {
//                    isWritingEntity = false;
//                }

                // 将是否为首包置为false，后面的开始发正式数据
                isMsgStart = false;

            } else {

                // 数据包 [1]-[4] 为 msgId
                msgIdByte = int2byte(msg_id);
                L.i("msgId = " + msg_id);
                // TODO: 2019/12/2 应该在收到 Server 回调的时候，做递增
                msg_id++;


                /**
                 * 数组拷贝
                 * 原数组
                 * 元数据的起始位置
                 * 目标数组
                 * 目标数组的开始起始位置
                 * 要 copy 的数组的长度
                 */
//                System.arraycopy(msgIdByte, 0, txBuffer, 1, BFrameConst.MESSAGE_ID_LENGTH);
                System.arraycopy(msgIdByte, 0, txBuffer, 0, BFrameConst.MESSAGE_ID_LENGTH);

                // 数据包 [5]-[18] 为内容
                for (int i = 4; i < onePackLength + 4; i++) {
                    if (index < dataLength) {
                        txBuffer[i] = data[index++];
                    }
                }
//                L.i("index = " + index);
//                L.i("onePackLength = " + onePackLength);
//                L.i("dataLength = " + dataLength);

                // 更新剩余数据长度
                availableLength -= onePackLength;

                // 单个数据包发送
//                boolean result = write(txBuffer);
                boolean result = BLEAdmin.getInstance(this).sendMessage(txBuffer);

            }

            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }

        // 连接间隔时间修改
//        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
//            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
//        }
        L.e("写入完成");

    }

    public static byte[] int2byte(int res) {
        byte[] targets = new byte[4];

        targets[3] = (byte) (res & 0xff);// 最低位
        targets[2] = (byte) ((res >> 8) & 0xff);// 次低位
        targets[1] = (byte) ((res >> 16) & 0xff);// 次高位
        targets[0] = (byte) (res >>> 24);// 最高位,无符号右移。
        return targets;
    }

//    /**
//     * 数据分包
//     *
//     * @param data 数据源
//     */
//    private void subpackageByte(byte[] data) {
//
//        isWritingEntity = true;
//        int index = 0;
//        int length = data.length;
//        int availableLength = length;
//
//        while (index < length) {
//
//            if (!isWritingEntity) {
//                L.e("写入取消");
//            }
//
//            // 每包大小为 20
//            int onePackLength = packLength;
//            //最后一包不足数据字节不会自动补零
//            if (!lastPackComplete) {
//                onePackLength = (availableLength >= packLength ? packLength : availableLength);
//            }
//
//            byte[] txBuffer = new byte[onePackLength];
//
//            txBuffer[0] = 0x00;
//            for (int i = 0; i < onePackLength; i++) {
//                if (index < length) {
//                    txBuffer[i] = data[index++];
//                }
//            }
//
//            availableLength -= onePackLength;
//
//            // 单个数据包发送
//            boolean result = BLEAdmin.getInstance(this).sendMessage(txBuffer);
//
//            if (!result) {
////                if(mBleEntityLisenter != null) {
////                    mBleEntityLisenter.onWriteFailed();
//                isWritingEntity = false;
//                isAutoWriteMode = false;
////                    return false;
////                }
//            } else {
////                if (mBleEntityLisenter != null) {
//                double progress = new BigDecimal((float) index / length).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
////                    mBleEntityLisenter.onWriteProgress(progress);
////                }
//            }
//
////            if (autoWriteMode) {
////                synchronized (lock) {
////                    try {
//////                        lock.wait(500);
////                        lock.wait();
////                    } catch (InterruptedException e) {
////                        e.printStackTrace();
////                    }
////                }
////            } else {
//            try {
//                Thread.sleep(100L);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
////            }
//        }
//
//        L.e("写入完成");
//
//    }

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
//    private void setAdapter() {
//
//        if (null == msgShowAdapter) {
//
//            msgShowAdapter = new MsgShowAdapter(MainActivity.this, msgList);
//            rcv_msg_show.setAdapter(msgShowAdapter);
//
//        } else {
//            // 刷新适配器
//            msgShowAdapter.update(msgList);
//        }
//
//    }

    private boolean isLoading = false;

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (keyCode == KeyEvent.KEYCODE_BACK) { // 监控/拦截/屏蔽返回键
//            // 短按按键1，弹框消失，确认连接
//            ll_ble_connect_info.setVisibility(View.GONE);
//
//        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
//            // 短按按键2，弹框消失，拒绝连接
//            BLEAdmin.getInstance(this).closeConnection();
//
//        } else if (keyCode == KeyEvent.KEYCODE_HOME) {
//            //这里操作是没有返回结果的
//        }
//        return super.onKeyDown(keyCode, event);
//    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        int keyCode = event.getKeyCode();
        Log.e(ContantValue.TAG, "keyCode == " + keyCode);

        // 正在初始化中，按键无效
        if (isLoading) {
            return true;
        }

        if (!isReceiveTokenComplete) {
            return true;
        }

        // 获取具体的按键类型
        KEY_ACTION_TYPE = getKeyActionType(keyCode, event);
        Log.e(ContantValue.TAG, "KEY_ACTION_TYPE == " + KEY_ACTION_TYPE);
        if (KEY_ACTION_TYPE == 3) {

            // 短按按键 1，弹框消失，确认连接，保存该设备到本地，下次可以自动连接不弹框
            L.i("同意连接");
            ll_ble_connect_info.setVisibility(View.GONE);
            // TODO: 2019/12/10 这里需要给一个状态值，用来做 Server 是否继续接收消息的判断
            BLEAdmin.getInstance(this).agreeConnection();
            // 刷新本地存储的主设备 Token
            updateRemoteTokenSP(remoteTokenReceived);

        } else if (KEY_ACTION_TYPE == 1) {

            L.i("拒绝连接");
            // 短按按键 2，弹框消失，拒绝连接
            ll_ble_connect_info.setVisibility(View.GONE);
            BLEAdmin.getInstance(this).closeConnection();

        }

        //在main 界面不能相应长按返回的操作，所以这里返回true，不再去base 中跑
        //return super.dispatchKeyEvent(event);
        return true;

    }

    /**
     * 刷新 SP 中存储的主设备 Token
     *
     * @param remoteTokenStr 待存入的主设备 Token
     */
    private void updateRemoteTokenSP(String remoteTokenStr) {

        // 保存设置语言的类型到 SP
        SharedPreferences sharedPreferences = getSharedPreferences(ContantValue.SP_BLE_REMOTE_INFO, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(ContantValue.SP_BLE_REMOTE_TOKEN, remoteTokenStr);
        // 确保存储成功
        saveRemoteToken(editor);

    }

    /**
     * 确保存储主设备 Token 成功
     *
     * @param editor
     */
    private void saveRemoteToken(SharedPreferences.Editor editor) {
        if (!editor.commit()) {
            saveRemoteToken(editor);
        }
    }

    int mKey1Action = KeyEvent.ACTION_UP;
    long mActionTime = 0;
    int mKey2Action = KeyEvent.ACTION_UP;
    long mAction1up = 0;
    long mAction2up = 0;
    boolean isback = false;
    boolean isReboot = false;

    final int KEY_BACK_TIME = 1000;
    final int KEY_REBOOT_TIME = 10000;
    final int KEY_SOS_TIME = 2000;
    public int KEY_ACTION_TYPE = 0;

    boolean isKeyMessageSend = false;

    public int getKeyActionType(int keyCode, KeyEvent event) {
        int KEY_ACTION_TYPE = 0;
        int action = event.getAction();

        if (keyCode == ContantValue.USER_KEYCODE_1 && action == KeyEvent.ACTION_DOWN) {
            mKey1Action = KeyEvent.ACTION_DOWN;  //记录按下状态
            if (mActionTime == 0) {
                mActionTime = System.currentTimeMillis();
            }
        }

        if (keyCode == ContantValue.USER_KEYCODE_1 && action == KeyEvent.ACTION_UP) {
            mKey1Action = KeyEvent.ACTION_UP;  //记录松下状态
            mAction1up = System.currentTimeMillis();

        }

        if (keyCode == ContantValue.USER_KEYCODE_2 && event.getAction() == KeyEvent.ACTION_DOWN) {
            mKey2Action = KeyEvent.ACTION_DOWN;   //记录按下状态
            if (mActionTime == 0) {
                mActionTime = System.currentTimeMillis();
            }
        }

        if (keyCode == ContantValue.USER_KEYCODE_2 && event.getAction() == KeyEvent.ACTION_UP) {
            mKey2Action = KeyEvent.ACTION_UP;    //记录松下状态
            mAction2up = System.currentTimeMillis();

        }

        if (mKey1Action == KeyEvent.ACTION_DOWN && mKey2Action == KeyEvent.ACTION_DOWN) {
            /*if (isLongPress12()) { //同时按俩键
                //长按，且Back键和OK键没松
                KEY_ACTION_TYPE = 5;
            } else {
                KEY_ACTION_TYPE = 6;
            }*/
            KEY_ACTION_TYPE = 6;
        } else if (mKey1Action == KeyEvent.ACTION_DOWN && mKey2Action == KeyEvent.ACTION_UP) {
            /*if (isLongPress1()) { //长按按键1
                KEY_ACTION_TYPE = 2;  //返回
            }else{

            }*/
            if (System.currentTimeMillis() - mActionTime > KEY_REBOOT_TIME) {
                KEY_ACTION_TYPE = 5;
            } else if (System.currentTimeMillis() - mActionTime > KEY_BACK_TIME) {
                KEY_ACTION_TYPE = 2;
            }
        } else if (mKey2Action == KeyEvent.ACTION_DOWN && mKey1Action == KeyEvent.ACTION_UP) {
            if (isLongPress2()) { //长按按键2  sos
                KEY_ACTION_TYPE = 4;
            }
        }

        if (mAction1up != 0) {
            if (mAction1up - mActionTime < KEY_BACK_TIME) {
                KEY_ACTION_TYPE = 1; //短按1 下一步
            }
        } else if (mAction2up != 0) {
            if (mAction2up - mActionTime < KEY_SOS_TIME) {
                KEY_ACTION_TYPE = 3; //短按2 确定
            }
        }

        if (KEY_ACTION_TYPE != 0 && KEY_ACTION_TYPE != 1 && KEY_ACTION_TYPE != 3) {
            if (KEY_ACTION_TYPE == 5 && isReboot == false) {
                isKeyMessageSend = false;
            }
            if (KEY_ACTION_TYPE == 2 && isback == false) {
                isKeyMessageSend = false;
            }

            if (isKeyMessageSend == false) {
                isKeyMessageSend = true;
                if (KEY_ACTION_TYPE == 2) {
                    isback = true;
                    KEY_ACTION_TYPE = 0;
                } else if (KEY_ACTION_TYPE == 5) {
                    isReboot = true;
                    // KEY_ACTION_TYPE = 0;
                }
                //LogUtil.e(" key   isKeyMessageSend  true" );
            } else {
                KEY_ACTION_TYPE = 0;
                //LogUtil.e(" key   isKeyMessageSend  无需重复发送 " );
            }
        }
        if (isback == true && mAction1up != 0) {
            KEY_ACTION_TYPE = 2;
            isback = false;
        }

        /*if(isReboot==true && mAction1up!=0 ){
            KEY_ACTION_TYPE = 5;
            isReboot = false;
        }*/

        if (mAction1up != 0 || mAction2up != 0) {
            isKeyMessageSend = false;
            mAction1up = 0;
            mAction2up = 0;
            mActionTime = 0;
        }

        LogUtil.d("   KEY_ACTION_TYPE=" + KEY_ACTION_TYPE);
        return KEY_ACTION_TYPE;
    }

    private boolean isLongPress2() {
        if (System.currentTimeMillis() - mActionTime > KEY_SOS_TIME) {
            return true;  //长按
        } else {
            return false;  //
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
//        rcv_msg_show = findViewById(R.id.rcv_msg_show);
//        llm = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
//        rcv_msg_show.setLayoutManager(llm);
        ll_ble_connect_info = findViewById(R.id.ll_ble_connect_info);
        tv_ble_device_name = findViewById(R.id.tv_ble_device_name);
        tv_ble_device_address = findViewById(R.id.tv_ble_device_address);
    }


}
