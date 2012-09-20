package com.media.medialabpttdownload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final String LOGIN_URL =
            "http://media.ee.ntu.edu.tw/group_meeting_ppt/web_search.php";
    private static final String PPT_URL =
            "http://media.ee.ntu.edu.tw/group_meeting_ppt/test_php/searchPPTu.php";

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
    private Dialog mRetryDialog;
    private String mUsername = "";
    private String mPassword = "";
    private String mPresenter = "*";
    private String mType = "*";
    private String mTitle = "";
    private String mDateFrom = "2000-01-01";
    private String mDateTo = "2100-08-31";
    private String mKeyword = "";
    private int mPageSize = 10;
    private int mStartIndex = 0;

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
                mUsername = username.getText().toString();
                mPassword = password.getText().toString();
                SharedPreferences.Editor editor = pref.edit();
                editor.putString(PREF_USERNAME, mUsername);
                editor.putString(PREF_PASSWORD, mPassword);
                editor.commit();
                query();
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

    private static final String ENCODING_TYPE = "utf-8";

    private void query() {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("presenterPat", mPresenter));
        nameValuePairs.add(new BasicNameValuePair("typePat", mType));
        nameValuePairs.add(new BasicNameValuePair("titlePat", mTitle));
        nameValuePairs.add(new BasicNameValuePair("datefrom", mDateFrom));
        nameValuePairs.add(new BasicNameValuePair("dateto", mDateTo));
        nameValuePairs.add(new BasicNameValuePair("semfrom", mDateFrom));
        nameValuePairs.add(new BasicNameValuePair("semto", mDateTo));
        nameValuePairs.add(new BasicNameValuePair("keywordPat", mKeyword));
        nameValuePairs.add(new BasicNameValuePair("pageSize", String.valueOf(mPageSize)));
        nameValuePairs.add(new BasicNameValuePair("startIndex",
                String.valueOf(mStartIndex)));
        String url = PPT_URL + "?" + URLEncodedUtils.format(nameValuePairs, ENCODING_TYPE);
        final HttpGet httpGet = new HttpGet(url);

        new AsyncTask<Void, Void, Integer>() {
            private ProgressDialog progress;
            private String resBody;
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
            protected Integer doInBackground(Void... params) {
                try {
                    Credentials creds = new UsernamePasswordCredentials(
                            mUsername, mPassword);
                    Header header = new BasicScheme().authenticate(creds, httpGet);
                    httpGet.setHeader(header);
                    HttpResponse response = mClient.execute(httpGet);
                    resBody = EntityUtils.toString(response.getEntity(), ENCODING_TYPE);
                    return response.getStatusLine().getStatusCode();
                } catch (AuthenticationException e) {
                    Log.e(TAG, "",e);
                } catch (ClientProtocolException e) {
                    Log.e(TAG, "",e);
                } catch (IOException e) {
                    Log.e(TAG, "",e);
                }
                return 0;
            }

            @Override
            protected void onPostExecute(Integer code) {
                progress.dismiss();
                if (code == 200) {
                    handleQueryResponse(resBody);
                } else if (code == 401) {
                    mLoginDialog.show();
                } else {
                    showRetryDialog();
                }
            }
        }.execute();
    }

    private void handleQueryResponse(String resBody) {
        Document doc = Jsoup.parse(resBody);
        Element table = doc.select("tbody").first();
        Elements list = table.children();
        for (int i = 0, n = list.size(); i < n; i++) {
            if(i % 2 == 0) continue;
            Element item = list.get(i);
            Elements data = item.children();
            String time = data.get(0).text();
            String type = data.get(1).text();
            String title = data.get(2).child(0).text();
            String presenter = data.get(3).text();
            String link = data.get(4).child(0).attr("href");
            Log.e(TAG, "Time: " + time);
            Log.e(TAG, "Type: " + type);
            Log.e(TAG, "Title: " + title);
            Log.e(TAG, "Presenter: " + presenter);
            Log.e(TAG, "Link: " + link);
        }
    }

    private void showRetryDialog() {
        if (mRetryDialog != null) {
            mRetryDialog.show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources().getString(R.string.network_error));
        builder.setPositiveButton(R.string.retry, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {
                query();
            }
        });
        builder.setCancelable(true);
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        mRetryDialog = builder.create();
        mRetryDialog.show();
    }
}
