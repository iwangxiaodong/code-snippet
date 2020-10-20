/*
dependencies {
    implementation('com.aliyun:aliyun-java-sdk-core:4.5.3')
    implementation group: 'xerces', name: 'xercesImpl', version: '2.12.0'
}
android {
    // ...
    packagingOptions {
        exclude 'META-INF/DEPENDENCIES'
        exclude 'META-INF/NOTICE'
        exclude 'META-INF/LICENSE'
        exclude 'META-INF/LICENSE.txt'
        exclude 'META-INF/NOTICE.txt'
    }
}
*/

package examples.aliyun.java.sdk.core.example;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.HttpClientConfig;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button).setOnClickListener(view -> new Thread(new Runnable() {
            @Override
            public void run() {
                execute();
            }
        }).start());
    }

    public static void execute() {
        DefaultProfile profile = DefaultProfile.getProfile("cn-shanghai", "<accessKeyId>", "<accessSecret>");

        //  Android专用 - 将默认的ApacheHttpClient改为HttpURLConnection
        HttpClientConfig httpClientConfig = HttpClientConfig.getDefault();
        httpClientConfig.setCompatibleMode(true);
        profile.setHttpClientConfig(httpClientConfig);

        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain("iot.cn-shanghai.aliyuncs.com");
        request.setSysVersion("2018-01-20");
        request.setSysAction("Pub");
        request.putQueryParameter("RegionId", "cn-shanghai");
        request.putQueryParameter("TopicFullName", "/ProductKeyValue/device1/user/get");
        byte[] bytes = new byte[]{(byte) 0xAC, (byte) 0xED, (byte) 0x00, (byte) 0x05};
        //String content = java.util.Base64.getEncoder().encodeToString(bytes);
        String content = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
        request.putQueryParameter("MessageContent", content);
        request.putQueryParameter("ProductKey", "ProductKeyValue");
        try {
            CommonResponse response = client.getCommonResponse(request);
            System.out.println(response.getData());
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (ClientException e) {
            e.printStackTrace();
        }
    }
}
