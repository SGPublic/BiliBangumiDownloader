package com.sgpublic.bilidownload.BangumiAPI;

import android.content.Context;
import android.util.Log;

import com.sgpublic.bilidownload.Unit.Base64Unit;
import com.sgpublic.bilidownload.DataItem.TokenData;
import com.sgpublic.bilidownload.R;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

import javax.crypto.Cipher;

import okhttp3.Call;
import okhttp3.Response;

public class LoginHelper {
    private static final String TAG = "LoginHelper";

    private String username;
    private String password;

    private String cookie;
    private String user_agent;

    private String hash;
    private String public_key = "";

    private Callback callback_private;
    private final APIHelper helper;

    private final Context context;

    public LoginHelper(Context context) {
        this.context = context;
        helper = new APIHelper();
    }

    public void loginInAccount(String username, String password, Callback callback) {
        this.username = username;
        this.password = password;
        this.callback_private = callback;
        getPublicKey();
    }

    private void getPublicKey() {
        Call call = helper.getKeyRequest();
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (e instanceof UnknownHostException) {
                    callback_private.onFailure(-101, context.getString(R.string.error_network), e);
                } else {
                    callback_private.onFailure(-102, e.getMessage(), e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String result = Objects.requireNonNull(response.body()).string();
                try {
                    JSONObject object = new JSONObject(result);
                    if (object.getInt("code") == 0) {
                        object = object.getJSONObject("data");
                        hash = object.getString("hash");
                        public_key = object.getString("key");
                        postAccount();
                    } else {
                        callback_private.onFailure(-104, null, null);
                    }
                } catch (JSONException e) {
                    callback_private.onFailure(-103, null, e);
                }
            }
        });
    }

    private void postAccount() {
        String password_encrypted = "";

        public_key = public_key.replace("\n", "").substring(26, 242);
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64Unit.Decode(public_key));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            Cipher cp = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cp.init(Cipher.ENCRYPT_MODE, pubKey);
            password_encrypted = Base64Unit.Encode(cp.doFinal(hash.concat(password).getBytes()));
            password_encrypted = URLEncoder.encode(password_encrypted, "UTF-8");
        } catch (Exception e) {
            callback_private.onFailure(-125, e.getMessage(), e);
            e.printStackTrace();
        }
        Call call = helper.getLoginRequest(username, password_encrypted);

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (e instanceof UnknownHostException) {
                    callback_private.onFailure(-121, context.getString(R.string.error_network), null);
                } else {
                    callback_private.onFailure(-122, e.getMessage(), e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String result = Objects.requireNonNull(response.body()).string();
                try {
                    JSONObject object = new JSONObject(result);
                    if (object.getInt("code") == 0) {
                        object = object.getJSONObject("data");
                        if (object.getInt("status") == 0) {
                            object = object.getJSONObject("token_info");
                            TokenData token = new TokenData();
                            token.access_token = object.getString("access_token");
                            token.refresh_token = object.getString("refresh_token");
                            token.expires_in = object.getLong("expires_in") * 1000L + Long.parseLong(APIHelper.getTS());
                            callback_private.onResult(token, object.getLong("mid"));
                        } else if (object.getInt("status") == 3 || object.getInt("status") == 2) {
                            callback_private.onLimited();
                        } else {
                            callback_private.onFailure(-126, null, null);
                        }
                    } else {
                        callback_private.onFailure(-124, object.getString("message"), null);
                    }
                } catch (JSONException e) {
                    callback_private.onFailure(-123, null, e);
                }
            }
        });
    }

    public void loginInWeb(String cookie, String user_agent, Callback callback) {
        this.cookie = cookie;
        this.user_agent = user_agent;
        this.callback_private = callback;
        getConfirmUri();
    }

    private void getConfirmUri() {
        Call call = helper.getLoginWebRequest(cookie, user_agent);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (e instanceof UnknownHostException) {
                    callback_private.onFailure(-131, context.getString(R.string.error_network), e);
                } else {
                    callback_private.onFailure(-132, e.getMessage(), e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String result = Objects.requireNonNull(response.body()).string();
                try {
                    JSONObject object = new JSONObject(result);
                    String confirm_uri = object.getJSONObject("data").getString("confirm_uri");
                    getAccessKey(confirm_uri);
                } catch (JSONException e) {
                    callback_private.onFailure(-133, null, e);
                }
            }
        });
    }

    private void getAccessKey(String confirm_uri) {
        Call call = helper.getLoginConfirmRequest(confirm_uri, cookie, user_agent);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (e instanceof UnknownHostException) {
                    callback_private.onFailure(-141, context.getString(R.string.error_network), e);
                } else {
                    callback_private.onFailure(-142, e.getMessage(), e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String location = response.header("Location");
                if (location != null && !Objects.equals(location, "")) {
                    if (location.startsWith("http://link.acg.tv/forum.php")) {
                        String[] url_split = location.split("&");
                        String access_key = url_split[0].substring(40);
                        long mid = Long.parseLong(url_split[1].substring(4));
                        TokenData token = new TokenData();
                        token.access_token = access_key;
                        token.refresh_token = "";
                        token.expires_in = 2592000000L + Long.parseLong(APIHelper.getTS());
                        callback_private.onResult(token, mid);
                    } else {
                        callback_private.onFailure(-144, null, null);
                    }
                } else {
                    callback_private.onFailure(-143, null, null);
                }
            }
        });
    }

    public void refreshToken(String access_token, String refresh_token, Callback callback){
        this.callback_private = callback;
        APIHelper helper = new APIHelper(access_token);
        Call call = helper.getRefreshTokenRequest(refresh_token);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (e instanceof UnknownHostException) {
                    callback_private.onFailure(-151, context.getString(R.string.error_network), e);
                } else {
                    callback_private.onFailure(-152, e.getMessage(), e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String result = Objects.requireNonNull(response.body()).string();
                Log.d(TAG, result);
                try {
                    JSONObject object = new JSONObject(result);
                    if (object.getInt("code") == 0) {
                        object = object.getJSONObject("data");
                        TokenData token = new TokenData();
                        token.access_token = object.getString("access_token");
                        token.refresh_token = object.getString("refresh_token");
                        token.expires_in = object.getLong("expires_in") * 1000L + Long.parseLong(APIHelper.getTS());
                        callback_private.onResult(token, object.getLong("mid"));
                    } else {
                        callback_private.onFailure(-154, object.getString("message"), null);
                    }
                } catch (JSONException e) {
                    callback_private.onFailure(-153, null, e);
                }
            }
        });
    }

    public interface Callback {
        void onFailure(int code, String message, Throwable e);
        void onLimited();
        void onResult(TokenData token, long mid);
    }
}
