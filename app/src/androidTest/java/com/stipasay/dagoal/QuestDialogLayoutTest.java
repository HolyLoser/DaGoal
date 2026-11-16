package com.stipasay.dagoal;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class QuestDialogLayoutTest {
 @Test public void questActionsRemainReadableAtCompactAndLargeFontSizes(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
   for(int width:new int[]{280,340,560})for(float font:new float[]{1f,1.5f}){
    Configuration config=new Configuration(base.getResources().getConfiguration());config.densityDpi=160;config.fontScale=font;config.screenWidthDp=width;
    Context ctx=new ContextThemeWrapper(base.createConfigurationContext(config),R.style.Theme_DaGoal);
    ScrollView root=(ScrollView)LayoutInflater.from(ctx).inflate(R.layout.dialog_quest_detail,null);
    ((TextView)root.findViewById(R.id.tv_quest_detail_title)).setText("Cook a meal");
    ((TextView)root.findViewById(R.id.tv_quest_detail_description)).setText("Cook a delicious homemade meal");
    ((TextView)root.findViewById(R.id.tv_quest_detail_reward)).setText("10 Gold  ·  15 XP");
    ((TextView)root.findViewById(R.id.tv_quest_detail_delete_cost)).setText(ctx.getString(R.string.quest_delete_cost,TaskManager.QUEST_DELETE_GOLD_COST));
    Button delete=root.findViewById(R.id.btn_quest_detail_delete);delete.setText(ctx.getString(R.string.quest_delete_button,TaskManager.QUEST_DELETE_GOLD_COST));
    root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST));root.layout(0,0,width,root.getMeasuredHeight());
    assertTrue(delete.getHeight()>=48);assertTrue(delete.getWidth()>0);assertTrue(delete.getText().toString().contains("1 Gold"));
    Button close=root.findViewById(R.id.btn_quest_detail_close);assertTrue(close.getHeight()>=48);
    TextView title=root.findViewById(R.id.tv_quest_detail_title);assertTrue(title.getWidth()>0);assertTrue(title.getRight()<=((View)title.getParent()).getWidth());
    assertTrue("Delete label must wrap instead of clip",delete.getLayout().getHeight()<=delete.getHeight()-delete.getCompoundPaddingTop()-delete.getCompoundPaddingBottom());
    if(width==340&&font==1f){
     Bitmap bitmap=Bitmap.createBitmap(width,root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
     try(FileOutputStream stream=new FileOutputStream(new File(base.getCacheDir(),"quest-dialog-updated.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}catch(Exception e){throw new AssertionError(e);}bitmap.recycle();
    }
   }
  });
 }
}
