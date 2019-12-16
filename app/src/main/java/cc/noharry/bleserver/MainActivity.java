package cc.noharry.bleserver;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import cc.noharry.bleserver.ble.IMsgSend;
import cc.noharry.bleserver.utils.AssetsUtil;
import cc.noharry.bleserver.utils.L;
import cc.noharry.bleserver.utils.LogUtil;
import cc.noharry.bleserver.utils.MySP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements OnClickListener,
        IMsgReceive, IMsgSend {

    private IBTOpenStateChange btOpenStateListener = null;

    private Button mBtInit;
    // 发送消息按钮
    private Button btn_send_msg;
    // 发送消息输入框
    private EditText et_msg_to_send;
    // 最终拼接消息显示
    private TextView tv_final_msg_show;

    /**
     * 用来保存数据分包的集合
     */
    private List<byte[]> contentBytes;

    private MyHandler mHandler;

    // 请求连接主机的名称和 MAC 地址
    private LinearLayout ll_ble_connect_info;
    private TextView tv_ble_device_name, tv_ble_device_address;

    // 获取开始接收消息和接收数据完成的时间
    private long startTimeMillis, endTimeMillis;

    // 接收到的消息类型
    private int msgType;

    // 本地保存的主设备 Token
    private String remoteTokenSP;
    // 接收到的主设备 Token
    private String remoteTokenReceived;
    // 是否正在接收 Token 数据，用来屏蔽按键
    private boolean isReceiveTokenComplete = false;


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

        mHandler = new MyHandler(this);

        // 找控件
        initView();

        // 点击事件监听
        setOnClickListener();

        // 初始化消息类型
        msgType = -1;

        // 初始化消息数据包的列表
        if (null == contentBytes) {
            contentBytes = new ArrayList<>();
        } else {
            contentBytes.clear();
        }

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
     * 获取消息列表数据
     */
    @Override
    public void onReceiveMsg(int msg_type, byte[] contentByte) {

        // 此时正在接收数据，拦截点击事件
        isReceiveTokenComplete = false;

        // 先看 msgType 是否一致，如果不一致则需要先将之前的数据包 list 清空
        if (msgType != msg_type) {
            // 获取开始时间
            startTimeMillis = System.currentTimeMillis();
            contentBytes.clear();
            // 即时刷新数据类型，避免影响下个数据包传过来时的判断
            msgType = msg_type;
        }

        // 添加接收到的 byte 数组到 list 中，接收完成之后做拼接
        this.contentBytes.add(contentByte);

        // 读到定义的数据包末尾，代表数据已经传输完毕
        if (contentByte[contentByte.length - 1] == (byte) 0x00) {

            // 此种类型的数据包接收完毕，重置数据类型，方便下次传数据的时候判断
            msgType = -1;
            // 获取结束时间
            endTimeMillis = System.currentTimeMillis();
            L.i("complete---耗时 = " + (endTimeMillis - startTimeMillis) + "ms---" + (endTimeMillis - startTimeMillis) / 1000 + "s");

            /**
             * 根据数据类型来做后续操作
             * 是 Token 还是普通内容数据
             * 普通内容数据之后还要分为用户信息、健康数据、位置信息等
             */
            switch (msg_type) {

                case BFrameConst.START_MSG_ID_TOKEN:
                    // 接收 Token 数据包完毕
                    receiveTokenCompleted();
                    break;

                case BFrameConst.START_MSG_ID_CENTRAL:
                    // 接收普通内容数据包完毕
                    receiveMsgComplete();
                    break;

                default:
                    break;

            }

        }

    }

    /**
     * 触发发送消息
     */
    @Override
    public void onSendMsg() {

        L.e("onSendMsg");

    }

    /**
     * 接收 Token 数据包完成
     */
    private void receiveTokenCompleted() {

        // 接收 Token 数据完成，可以点击
        isReceiveTokenComplete = true;

        if (null != contentBytes && contentBytes.size() != 0) {

            // 计算总字节长度
            int contentByteLength = contentBytes.size() * 20;
            // 待拼接数组，最终用来转换字符串显示
            byte[] contentBytesConcat = new byte[contentByteLength];
            for (int i = 0; i < contentBytes.size(); i++) {
                System.arraycopy(contentBytes.get(i), 0, contentBytesConcat, i * 20, 20);
            }
            // 转成字符串
            remoteTokenReceived = new String(contentBytesConcat);

            L.e("onReceiveTokenComplete---remoteTokenReceived = " + remoteTokenReceived);
            L.e("onReceiveTokenComplete---remoteTokenSP = " + remoteTokenSP);
            L.e("onReceiveTokenComplete---remoteTokenSP.isEmpty = " + TextUtils.isEmpty(remoteTokenSP));
            // 将收到的主设备 Token 和本地的对比，如果不一致则需要用户授权
            // 如果本地存储为空
            if (TextUtils.isEmpty(remoteTokenSP)) {

                L.e("本地为空，需要弹框用户授权");

                // 弹用户授权对话框
                showUserAuthDialog();

            } else {

                if (!TextUtils.isEmpty(remoteTokenReceived)) {

                    boolean isMatch = TextUtils.equals(remoteTokenReceived, remoteTokenSP);
                    L.e("onReceiveTokenComplete---remoteTokenSP = " + isMatch);
                    // 不匹配，则需要用户授权
                    if (!isMatch) {
                        // 弹用户授权对话框
                        showUserAuthDialog();

                    } else {

                        L.e("token 匹配，直接连接");
                        // 不需要用户授权，直接连接
                        ll_ble_connect_info.setVisibility(View.GONE);
                        // 将判断值设置为同意连接，这样可以接收到后面传输的内容数据包
                        BLEAdmin.getInstance(this).agreeConnection();
                        // TODO: 2019/12/13 具体发送消息的触发条件可以在其他地方，但是都先需要将 BLEAdmin 中的 isUserAuth 置为true
                        String contentStr = AssetsUtil.getJson("BLE100组健康数据示例.txt", getApplicationContext());
                        // 给 Handler 传参数，准备预分包，即字符串转 byte[]
                        Message msgSendContent = mHandler.obtainMessage();
                        msgSendContent.what = BFrameConst.START_MSG_ID_SERVER;
                        msgSendContent.obj = contentStr;
                        mHandler.sendMessage(msgSendContent);

                    }

                } else {
                    // 2019/12/12 这里主设备未传 token，应该直接断开，弹框消失，拒绝连接
                    ll_ble_connect_info.setVisibility(View.GONE);
                    BLEAdmin.getInstance(this).closeConnection();
                }

            }

        }

    }

    /**
     * 弹框需要用户授权，同意主设备连接
     */
    private void showUserAuthDialog() {
        // TODO: 2019/12/10 弹框需要用户授权，同意连接才刷新本地 SP
        ll_ble_connect_info.setVisibility(View.VISIBLE);
        tv_ble_device_address.setVisibility(View.GONE);
    }

    /**
     * 接收普通内容数据包完成
     */
    private void receiveMsgComplete() {

        L.i("onReceiveMsgComplete");
        if (null != contentBytes && contentBytes.size() != 0) {

            // 计算总字节长度
            int contentByteLength = contentBytes.size() * 20;

            // 待拼接数组，最终用来转换字符串显示
            byte[] contentBytesConcat = new byte[contentByteLength];
            for (int i = 0; i < contentBytes.size(); i++) {
                System.arraycopy(contentBytes.get(i), 0, contentBytesConcat, i * 20, 20);
            }

            // 转成字符串
            String finalStr = new String(contentBytesConcat);
            LogUtil.showLogCompletion("corey", "onReceiveMsgComplete---finalStr = " + finalStr.trim(), 500);

            // 显示
            tv_final_msg_show.setText(finalStr);

        }

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
                subpackageByte(dataBytes, BFrameConst.START_MSG_ID_CENTRAL);
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

    private boolean isLoading = false;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        int keyCode = event.getKeyCode();
        Log.e(ContantValue.TAG, "keyCode == " + keyCode);

        // 正在初始化中，按键无效
        if (isLoading) {
            return true;
        }

        // 如果正在接收 Token 数据，则拦截点击
        if (!isReceiveTokenComplete) {
            return true;
        }

        // 获取具体的按键类型
        KEY_ACTION_TYPE = getKeyActionType(keyCode, event);
        Log.e(ContantValue.TAG, "KEY_ACTION_TYPE == " + KEY_ACTION_TYPE);
        if (KEY_ACTION_TYPE == 3) {

            // 短按按键 1，弹框消失，确认连接，保存该设备到本地，下次可以自动连接不弹框
            L.i("同意连接");
            // Token 变量刷新
            remoteTokenSP = remoteTokenReceived;
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

    /**
     * 自定义一个静态类，防止内存泄漏
     */
    private static class MyHandler extends Handler {

        WeakReference<MainActivity> mainActivity;

        public MyHandler(MainActivity mainActivity) {
            this.mainActivity = new WeakReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {

            final MainActivity act = mainActivity.get();

            switch (msg.what) {

                case BFrameConst.START_MSG_ID_SERVER:

                    /**
                     * 发送实际内容
                     * 第一个参数为发送内容，第二个参数为数据包类型：TOKEN/发送内容
                     */
                    L.e("Handler 准备发送数据");
                    L.e("Handler 准备发送数据" + msg.obj);
                    BLEAdmin.getInstance(act).sendMessageActively((String) msg.obj, BFrameConst.START_MSG_ID_SERVER);
                    break;

            }

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
