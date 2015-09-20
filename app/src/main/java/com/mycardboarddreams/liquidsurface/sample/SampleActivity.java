package com.mycardboarddreams.liquidsurface.sample;

import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;

import com.mycardboarddreams.liquidsurface.ILiquidWorld;
import com.google.fpl.liquidfunpaint.renderer.GameLoop;
import com.google.fpl.liquidfunpaint.SolidWorld;
import com.google.fpl.liquidfunpaint.util.Vector2f;


public class SampleActivity extends AppCompatActivity implements View.OnTouchListener {

    ILiquidWorld ltv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);

        ltv = (ILiquidWorld) findViewById(R.id.liquid_texture_view);

        ltv.setOnTouchListener(this);
    }

    /**
     * Make sure you call the following onResume() and onPause()
     */
    @Override
    protected void onResume() {
        super.onResume();
        ltv.clearAll();
        ltv.createLiquidShape(createCircle(getCenterPoint(), 400, 8));

        ltv.createSolidShape(createCircle(getCenterPoint(), 70, 8));

        ltv.resumePhysics();
    }

    private Vector2f getCenterPoint(){

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        Vector2f center = new Vector2f(size.x / 2, size.y / 2);
        return center;
    }

    private void createLiquidCircle() {

    }

    @Override
    protected void onPause() {
        super.onPause();
        ltv.pausePhysics();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        if(action == MotionEvent.ACTION_DOWN) {
            if(event.getX() > GameLoop.getInstance().sScreenWidth/2)
                SolidWorld.getInstance().spinWheel(-0.5f);
            else
                SolidWorld.getInstance().spinWheel(0.5f);
        } else if(action == MotionEvent.ACTION_UP){
            SolidWorld.getInstance().spinWheel(0);
        }

        return true;
    }

    private Vector2f[] createCircle(Vector2f center, float radius, int numPoints){
        Vector2f[] vertices = new Vector2f[numPoints];

        double angle = 2*Math.PI/numPoints;

        for(int i = 0; i < numPoints; i++){
            vertices[i] = new Vector2f(center.x + (float) (radius*Math.cos(i*angle)),
                                         center.y + (float) (radius*Math.sin(i*angle)));
        }

        return vertices;
    }

    private Vector2f[] createBox(Vector2f center, float width, float height){
        Vector2f[] vertices = new Vector2f[4];

        vertices[0] = new Vector2f(center.x - width/2, center.y + height/2);

        vertices[1] = new Vector2f(center.x + width/2, center.y + height/2);

        vertices[2] = new Vector2f(center.x - width/2, center.y - height/2);

        vertices[3] = new Vector2f(center.x + width/2, center.y - height/2);

        return vertices;
    }
}
