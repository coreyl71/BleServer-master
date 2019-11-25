package cc.noharry.bleserver;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import cc.noharry.bleserver.ble.BLEAdmin;
import cc.noharry.bleserver.ble.OnBTOpenStateListener;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements OnClickListener {

  private OnBTOpenStateListener btOpenStateListener = null;

  private Button mBtInit;
  private UUID UUID_SERVER=UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb");
  private TextView mTv_times;
  public AtomicInteger times=new AtomicInteger(0);

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

    initView();
    initEvent();

  }

  private void initEvent() {
    mBtInit.setOnClickListener(this);
  }

  private void initView() {
    mBtInit = findViewById(R.id.bt_init);
  }

  @Override
  public void onClick(View v) {

    switch (v.getId()){

      case R.id.bt_init:

        // 开启蓝牙
        openBT();
        break;

      default:
        break;

    }

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
