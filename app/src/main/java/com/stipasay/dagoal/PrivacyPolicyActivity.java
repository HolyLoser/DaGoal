package com.stipasay.dagoal;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;

/** Bundled policy remains readable without a connection or account. */
public class PrivacyPolicyActivity extends AppCompatActivity {
 @Override protected void onCreate(Bundle state) {
  super.onCreate(state);
  AppearanceHelper.applyPreferredNightMode(this);
  setContentView(R.layout.activity_privacy_policy);
  AppearanceHelper.setupEdgeToEdgeInsets(findViewById(R.id.root_privacy_policy));
  androidx.core.view.WindowInsetsControllerCompat bars=androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());
  bars.setAppearanceLightStatusBars(true);
  bars.setAppearanceLightNavigationBars(true);
  findViewById(R.id.btn_privacy_back).setOnClickListener(v->finish());
  WebView content=findViewById(R.id.web_privacy_policy);
  WebSettings settings=content.getSettings();settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  settings.setTextZoom(Math.round(100f*getResources().getConfiguration().fontScale));
  content.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    Uri uri=request.getUrl();String scheme=uri.getScheme();
    if("https".equals(scheme)||"mailto".equals(scheme)){
     try{startActivity(new Intent("mailto".equals(scheme)?Intent.ACTION_SENDTO:Intent.ACTION_VIEW,uri));}
     catch(ActivityNotFoundException error){ToastUtils.showToast(PrivacyPolicyActivity.this,getString(R.string.privacy_link_unavailable));}
    }
    return true;
   }
  });
  content.loadUrl("file:///android_asset/privacy-policy.html");
 }
}
