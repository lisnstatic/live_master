package tcking.github.com.giraffeplayer.example;

public class Message {

    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_LOG = 1;
    public static final int TYPE_ACTION = 2;

    private int mType;
    private String mMessage;

    private String mMessageColor;
    private String mUsername;

    private Message() {}

    public int getType() {
        return mType;
    };

    public String getMessage() {
        return mMessage;
    };

    public String getUsername() {
        return mUsername;
    };
    public String getmMessageColor() {
        return mMessageColor;
    }

    public void setmMessageColor(String mMessageColor) {
        this.mMessageColor = mMessageColor;
    }


    public static class Builder {
        private final int mType;
        private String mUsername;
        private String mMessage;
        private String mMessageColor;

        public Builder(int type) {
            mType = type;
        }

        public Builder username(String username) {
            mUsername = username;
            return this;
        }

        public Builder message(String message) {
            mMessage = message;
            return this;
        }

        public Builder messagecolor(String messagecolor) {
            mMessageColor = messagecolor;
            return this;
        }

        public Message build() {
            Message message = new Message();
            message.mType = mType;
            message.mUsername = mUsername;
            message.mMessage = mMessage;
            message.mMessage = mMessage;
            message.mMessageColor = mMessageColor;
            return message;
        }
    }
}
