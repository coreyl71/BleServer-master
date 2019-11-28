package cc.noharry.bleserver.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import cc.noharry.bleserver.R;
import cc.noharry.bleserver.bean.MsgBean;

public class MsgShowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context mContext;

    private List<MsgBean> msgList;

    // 适配器构造方法
    public MsgShowAdapter(Context mContext, List<MsgBean> msgList) {
        this.mContext = mContext;
        this.msgList = msgList;
    }

    // 更新数据源
    public void update(List<MsgBean> msgList) {
        this.msgList = msgList;
        this.notifyDataSetChanged();
    }

    // 局部刷新
    public void updateItem(List<MsgBean> msgList, int position) {
        this.msgList = msgList;
        this.notifyItemChanged(position, "corey");
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        RecyclerView.ViewHolder holder = null;
        View convertView = null;

        convertView = LayoutInflater.from(mContext).inflate(R.layout.item_change_country, parent, false);
//        convertView.setOnClickListener(this);
        holder = new FundBottomHolder(convertView);

        return holder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

        FundBottomHolder holder1 = (FundBottomHolder) holder;

        // HEX 字符串显示
        if (!TextUtils.isEmpty(msgList.get(position).getHexStr())) {
            holder1.tv_item_hex_show.setText(msgList.get(position).getHexStr());
        }

        // 消息字符串显示
        if (!TextUtils.isEmpty(msgList.get(position).getMsgShowStr())) {
            holder1.tv_item_msg_show.setText(msgList.get(position).getMsgShowStr());
        }

        // 消息位置显示
        if (!TextUtils.isEmpty(msgList.get(position).getMsgShowStr())) {
            holder1.tv_item_msg_position_show.setText(msgList.get(position).getMsgId());
        }

        holder1.itemView.setTag(position);

    }

    /**
     * 对小数位数进行转换
     *
     * @param decimal 保留小数的位数
     * @param obj     待转换的目标
     * @return
     */
    private String formatPriceNAmount(String decimal, String obj) {

        String result = "--";

        if (null != obj && !obj.equals("")
                && null != decimal && !decimal.equals("")) {
            try {
                double last_price_f = Double.parseDouble(obj);
                result = String.format("%." + decimal + "f", last_price_f);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }

        }

        return result;

    }

    @Override
    public int getItemCount() {
        return msgList == null ? 0 : msgList.size();
    }

    // 搜索列表item布局
    class FundBottomHolder extends RecyclerView.ViewHolder {

        // 英文名称
        TextView tv_item_hex_show;
        // 中文名称
        TextView tv_item_msg_show;
        // 编号
        TextView tv_item_msg_position_show;
        // 分割线
        View view_rcv_divider;
        View itemView;

        public FundBottomHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;

            tv_item_hex_show = (TextView) itemView.findViewById(R.id.tv_item_hex_show);
            tv_item_msg_show = (TextView) itemView.findViewById(R.id.tv_item_msg_show);
            tv_item_msg_position_show = (TextView) itemView.findViewById(R.id.tv_item_msg_position_show);

            view_rcv_divider = (View) itemView.findViewById(R.id.view_rcv_divider);

        }
    }

}
