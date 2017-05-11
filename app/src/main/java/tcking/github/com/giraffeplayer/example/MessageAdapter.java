package tcking.github.com.giraffeplayer.example;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;


public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private List<Message> mMessages;
    private int[] mUsernameColors;
    private  String textColor;

    public MessageAdapter(Context context, List<Message> messages) {
        mMessages = messages;
        mUsernameColors = context.getResources().getIntArray(R.array.username_colors);
    }
   /* public MessageAdapter(MainActivity context, List<android.os.Message> messages) {
        mMessages = messages;
        mUsernameColors = context.getResources().getIntArray(R.array.username_colors);
    }*/

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout = -1;
        switch (viewType) {
        case Message.TYPE_MESSAGE:
            layout = R.layout.item_message;
            break;
        case Message.TYPE_LOG:
            layout = R.layout.item_log;
            break;
        case Message.TYPE_ACTION:
            layout = R.layout.item_action;
            break;
        }
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        Message message = mMessages.get(position);
        viewHolder.setMessage(message.getMessage());
        viewHolder.setUsername(message.getUsername());
        viewHolder.setMessageColor(message.getmMessageColor());
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mMessages.get(position).getType();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mUsernameView;
        private TextView mMessageView;

        public ViewHolder(View itemView) {
            super(itemView);

            mUsernameView = (TextView) itemView.findViewById(R.id.username);
            mMessageView = (TextView) itemView.findViewById(R.id.message);
        }

        public void setUsername(String username) {
            if (null == mUsernameView) return;
            mUsernameView.setText(username+":");
            mUsernameView.setTextColor(getUsernameColor(username));

            /*String color = Integer.toHexString((int)Integer.parseInt(textColor));
            Log.i("color1",color);*/

        }

        public void setMessage(String message) {
            if (null == mMessageView) return;
            mMessageView.setText(message);
        }

        public void setMessageColor(String messageolor) {
            if (null == mMessageView) return;

            String color = "#"+Integer.toHexString(Integer.parseInt(messageolor));
            Log.i("color", color + "");
            if (color.equals("#0")){
                color = "#000000";
            }
            /*mUsernameView.setTextColor(Color.parseColor(color));*/
            mMessageView.setTextColor(Color.parseColor(color));
        }

        private int getUsernameColor(String username) {
            /*int hash = 7;
            for (int i = 0, len = username.length(); i < len; i++) {
                hash = username.codePointAt(i) + (hash << 5) - hash;
            }
            int index = Math.abs(hash % mUsernameColors.length);*/
            if (username.equals(WatchLiveActivity.mUserId)){
                return mUsernameColors[0];
            }else{
                return mUsernameColors[1];
            }

        }
    }
}
