package com.media.medialabpttdownload;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final String LOGIN_URL =
            "http://media.ee.ntu.edu.tw/group_meeting_ppt/web_search.php";

    private static final String PREF = "pref";
    private static final String PREF_USERNAME = "username";
    private static final String PREF_PASSWORD = "password";

    private static final int REQUEST_TIMEOUT = 10000;

    private static final DefaultHttpClient mClient;
    static {
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, REQUEST_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParams, REQUEST_TIMEOUT);
        mClient = new DefaultHttpClient(httpParams);
    }

    private Dialog mLoginDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        buildLoginDialog();
        mLoginDialog.show();
    }

    private void buildLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.login_dialog_title));
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.login_dialog, null);
        builder.setView(view);
        final EditText username = (EditText) view.findViewById(R.id.username);
        final EditText password = (EditText) view.findViewById(R.id.password);
        final SharedPreferences pref = getSharedPreferences(PREF, 0);
        username.setText(pref.getString(PREF_USERNAME, ""));
        password.setText(pref.getString(PREF_PASSWORD, ""));
        builder.setPositiveButton(getResources().getString(R.string.login)
                , new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor editor = pref.edit();
                editor.putString(PREF_USERNAME, username.getText().toString());
                editor.putString(PREF_PASSWORD, password.getText().toString());
                editor.commit();
                login(username.getText().toString()
                        , password.getText().toString());
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        mLoginDialog = builder.create();
    }

    private void login(final String username, final String password) {
        new AsyncTask<Void, Void, Boolean> () {
            private ProgressDialog progress;
            @Override
            protected void onPreExecute() {
                progress = ProgressDialog.show(MainActivity.this
                        , "", "Wait for media.ee.ntu.edu.tw");
                progress.setCancelable(true);
                progress.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });
            }
            @Override
            protected void onPostExecute(Boolean result) {
                progress.dismiss();
                if (result) {
                    loginSuccess();
                } else {
                    Toast.makeText(MainActivity.this, R.string.login_error
                            , Toast.LENGTH_SHORT).show();
                    mLoginDialog.show();
                }
            }
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    HttpGet httpGet = new HttpGet(LOGIN_URL);
                    Credentials creds = new UsernamePasswordCredentials(
                            username, password);
                    Header header = new BasicScheme().authenticate(creds, httpGet);
                    httpGet.setHeader(header);
                    HttpResponse response = mClient.execute(httpGet);
                    return response.getStatusLine().getStatusCode() == 200;
                } catch (ClientProtocolException e) {
                    Log.e(TAG, "", e);
                } catch (IOException e) {
                    Log.e(TAG, "", e);
                } catch (AuthenticationException e) {
                    Log.e(TAG, "", e);
                }
                return false;
            }
        }.execute();
    }

    private void loginSuccess() {

    }
}
