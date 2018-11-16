package com.earthgee.echo;

import android.util.Log;

/**
 * Created by zhaoruixuan on 2018/11/12.
 */

public class EchoClient {

    private static final String TAG = "EchoClient";

    private final LongLiveSocket mLongLiveSocket;

    public EchoClient(String host, int port) {
        mLongLiveSocket = new LongLiveSocket(host, port, new LongLiveSocket.DataCallback() {
            @Override
            public void onData(byte[] data, int offset, int len) {
                Log.i(TAG, "EchoClient: received: "+new String(data, offset, len));
            }
        }, new LongLiveSocket.ErrorCallback() {
            @Override
            public boolean onError() {
                return true;
            }
        });
    }

    public void send(String msg){
        mLongLiveSocket.write(msg.getBytes(), new LongLiveSocket.WritingCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "write success");
            }

            @Override
            public void onFail(byte[] data, int offset, int len) {
                Log.i(TAG, "write fail");
                mLongLiveSocket.write(data, offset, len, this);
            }
        });
    }

    public void close(){
        mLongLiveSocket.close();
    }

}



























