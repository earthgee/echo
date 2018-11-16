package com.earthgee.echo;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Created by zhaoruixuan on 2018/11/13.
 */

public class LongLiveSocket {

    private static final String TAG = "LongLiveSocket";

    private static final long RETRY_INTERVAL_MILLS = 3*1000;
    private static final long HEART_BEAT_INTERVAL_MILLS = 5*1000;
    private static final long HEART_BEAT_TIMEOUT_MILLS = 2*1000;

    public interface ErrorCallback {
        boolean onError();
    }

    public interface DataCallback {
        void onData(byte[] data, int offset, int len);
    }

    public interface WritingCallback {
        void onSuccess();
        void onFail(byte[] data, int offset, int len);
    }

    private final String mHost;
    private final int mPort;
    private final DataCallback mDataCallback;
    private final ErrorCallback mErrorCallback;

    private final HandlerThread mWriterThread;
    private final Handler mWriterHandler;
    private final Handler mUIHandler=new Handler(Looper.getMainLooper());

    private final Object mLock=new Object();
    private Socket mSocket;
    private boolean mClosed;

    private final Runnable mHeartBeatTask = new Runnable() {
        private byte[] mHeartBeat = new byte[0];

        @Override
        public void run() {
            write(mHeartBeat, new WritingCallback() {
                @Override
                public void onSuccess() {
                    mWriterHandler.postDelayed(mHeartBeatTask, HEART_BEAT_INTERVAL_MILLS);
                    mUIHandler.postDelayed(mHeartBeatTimeoutTask, HEART_BEAT_TIMEOUT_MILLS);
                }

                @Override
                public void onFail(byte[] data, int offset, int len) {

                }
            });
        }
    };

    private final Runnable mHeartBeatTimeoutTask=new Runnable() {
        @Override
        public void run() {
            closeSocket();
        }
    };

    public LongLiveSocket(String host, int port, DataCallback dataCallback, ErrorCallback errorCallback){
        mHost = host;
        mPort = port;
        mDataCallback = dataCallback;
        mErrorCallback = errorCallback;

        mWriterThread = new HandlerThread("socket-writer");
        mWriterThread.start();
        mWriterHandler = new Handler(mWriterThread.getLooper());
        mWriterHandler.post(new Runnable() {
            @Override
            public void run() {
                initSocket();
            }
        });
    }

    private void initSocket(){
        while(true) {
            if(closed()) return;

            try{
                Socket socket=new Socket(mHost,mPort);
                synchronized (mLock) {
                    if(mClosed) {
                        silentlyClose(socket);
                        return;
                    }
                    mSocket = socket;
                    Thread reader = new Thread(new ReaderTask(socket), "socket-reader");
                    reader.start();
                    mWriterHandler.post(mHeartBeatTask);
                }
                break;
            }catch (IOException e){
                if(closed()|| !mErrorCallback.onError()) {
                    break;
                }
                try{
                    TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL_MILLS);
                } catch (InterruptedException e1){
                    break;
                }
            }
        }
    }

    public void write(byte[] data, WritingCallback callback){
        write(data, 0, data.length, callback);
    }

    public void write(final byte[] data, final int offset, final int len, final WritingCallback callback){
        mWriterHandler.post(new Runnable() {
            @Override
            public void run() {
                Socket socket = getSocket();
                if(socket == null){
                    throw new IllegalStateException("Socket not initialized");
                }
                try{
                    OutputStream outputStream = socket.getOutputStream();
                    DataOutputStream out = new DataOutputStream(outputStream);
                    out.writeInt(len);
                    out.write(data, offset, len);
                    callback.onSuccess();
                }catch (IOException e){
                    closeSocket();
                    callback.onFail(data, offset, len);
                    if(!closed() && mErrorCallback.onError()){
                        initSocket();
                    }
                }
            }
        });
    }

    private boolean closed() {
        synchronized (mLock){
            return mClosed;
        }
    }

    private Socket getSocket(){
        synchronized (mLock) {
            return mSocket;
        }
    }

    private void closeSocket(){
        synchronized (mLock){
            closeSocketLocked();
        }
    }

    private void closeSocketLocked(){
        if(mSocket == null){
            return;
        }

        silentlyClose(mSocket);
        mSocket=null;
        mWriterHandler.removeCallbacks(mHeartBeatTask);
    }

    public void close(){
        if(Looper.getMainLooper() == Looper.myLooper()){
            new Thread(){
                @Override
                public void run() {
                    doClose();
                }
            }.start();
        }else{
            doClose();
        }
    }

    private void doClose(){
        synchronized (mLock){
            mClosed=true;
            closeSocketLocked();
        }
        mWriterThread.quit();
        mWriterThread.interrupt();
    }

    private static void silentlyClose(Closeable closeable){
        if(closeable!=null){
            try{
                closeable.close();
            }catch (IOException e){
                //error ignored
            }
        }
    }

    private class ReaderTask implements Runnable {

        private final Socket mSocket;

        public ReaderTask(Socket socket){
            mSocket = socket;
        }

        @Override
        public void run() {
            try{
                readResponse();
            }catch (IOException e){

            }
        }

        private void readResponse() throws IOException {
            byte[] buffer = new byte[1024];
            InputStream inputStream = mSocket.getInputStream();
            DataInputStream in = new DataInputStream(inputStream);
            while (true){
                int nbyte = in.readInt();
                if(nbyte == 0){
                    //心跳
                    Log.i(TAG, "readResponse: heart beat received");
                    mUIHandler.removeCallbacks(mHeartBeatTimeoutTask);
                    continue;
                }

                if(nbyte > buffer.length) {
                    throw new IllegalStateException("Receive message with len "+nbyte+" which exceeds limit "+buffer.length);
                }

                if(readn(in, buffer, nbyte)!=0){
                    silentlyClose(mSocket);
                    break;
                }
                mDataCallback.onData(buffer,0,nbyte);
            }
        }

        private int readn(InputStream in, byte[] buffer, int n) throws IOException{
            int offset = 0;
            while(n>0){
                int readBytes = in.read(buffer, offset, n);
                if(readBytes<0){
                    break;
                }
                n-=readBytes;
                offset+=readBytes;
            }
            return n;
        }

    }

}




































