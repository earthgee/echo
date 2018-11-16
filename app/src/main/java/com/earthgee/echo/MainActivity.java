package com.earthgee.echo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private EchoServer mEchoServer;
    private EchoClient mEchoClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final int port = 9877;
        mEchoServer = new EchoServer(port);
        mEchoServer.run();
        mEchoClient = new EchoClient("127.0.0.1", port);

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEchoClient.send("test");
            }
        });

    }
}

















