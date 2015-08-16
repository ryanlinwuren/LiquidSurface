package com.google.fpl.liquidfunpaint;

import android.util.Log;

import com.google.fpl.liquidfun.Body;
import com.google.fpl.liquidfun.BodyDef;
import com.google.fpl.liquidfun.BodyType;
import com.google.fpl.liquidfun.ParticleSystem;
import com.google.fpl.liquidfun.PolygonShape;
import com.google.fpl.liquidfun.World;
import com.mycardboarddreams.liquidsurface.BuildConfig;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by PC on 8/13/2015.
 */
public class LiquidWorld {
    private World mWorld = null;
    private Lock mWorldLock = new ReentrantLock();

    private static final float WORLD_SPAN = 3f;
    public float sRenderWorldWidth = WORLD_SPAN;
    public float sRenderWorldHeight = WORLD_SPAN;

    // Parameters for world simulation
    private static final int VELOCITY_ITERATIONS = 6;
    private static final int POSITION_ITERATIONS = 2;
    private static final int PARTICLE_ITERATIONS = 5;

    private static final float BOUNDARY_THICKNESS = 20.0f;

    private static final String TAG = "Renderer";
    private static final int ONE_SEC = 1000000000;

    // Measure the frame rate
    long totalFrames = -10000;
    private int mFrames;
    private long mStartTime;
    private long mTime;

    private Body mBoundaryBody = null;

    private static LiquidWorld sInstance = new LiquidWorld();

    public static LiquidWorld getInstance(){
        return sInstance;
    }

    void initializeWorldDimensions(int width, int height){

        if(height < width) { //landscape
            sRenderWorldHeight = WORLD_SPAN;
            sRenderWorldWidth = width * WORLD_SPAN / height;
        } else { //portrait
            sRenderWorldHeight = height * WORLD_SPAN / width;
            sRenderWorldWidth = WORLD_SPAN;
        }

        // Reset the boundary
        initBoundaries();
    }

    public boolean hasWorld(){
        return mWorld != null;
    }

    /** Constructs boundaries for the canvas. **/
    void initBoundaries() {
        World world = acquireWorld();

        try {
            // clean up previous Body if exists
            if (mBoundaryBody != null) {
                world.destroyBody(mBoundaryBody);
            }

            // Create native objects
            BodyDef bodyDef = new BodyDef();
            PolygonShape boundaryPolygon = new PolygonShape();

            mBoundaryBody = world.createBody(bodyDef);

            // boundary definitions
            // top
            boundaryPolygon.setAsBox(
                    sRenderWorldWidth,
                    BOUNDARY_THICKNESS,
                    sRenderWorldWidth / 2,
                    sRenderWorldHeight + BOUNDARY_THICKNESS,
                    0);
            mBoundaryBody.createFixture(boundaryPolygon, 0.0f);
            // bottom
            boundaryPolygon.setAsBox(
                    sRenderWorldWidth,
                    BOUNDARY_THICKNESS,
                    sRenderWorldWidth / 2,
                    -BOUNDARY_THICKNESS,
                    0);
            mBoundaryBody.createFixture(boundaryPolygon, 0.0f);
            // left
            boundaryPolygon.setAsBox(
                    BOUNDARY_THICKNESS,
                    sRenderWorldHeight,
                    -BOUNDARY_THICKNESS,
                    sRenderWorldHeight / 2,
                    0);
            mBoundaryBody.createFixture(boundaryPolygon, 0.0f);
            // right
            boundaryPolygon.setAsBox(
                    BOUNDARY_THICKNESS,
                    sRenderWorldHeight,
                    sRenderWorldWidth + BOUNDARY_THICKNESS,
                    sRenderWorldHeight / 2,
                    0);
            mBoundaryBody.createFixture(boundaryPolygon, 0.0f);

            // Clean up native objects
            bodyDef.delete();
            boundaryPolygon.delete();
        } finally {
            releaseWorld();
        }
    }

    void createNewWorld(){
        deleteWorld();
        mWorld = new World(0, 0);

        initBoundaries();

        initParticleSystem();
    }


    /** Create a new particle system */
    void initParticleSystem() {
        ParticleSystems.getInstance().resetToDefaultParticleSystem();
    }

    void deleteWorld() {
        World world = acquireWorld();

        try {
            if (mBoundaryBody != null) {
                mBoundaryBody.delete();
                mBoundaryBody = null;
            }
            if (world != null) {
                world.delete();
                mWorld = null;
                ParticleSystems.getInstance().clear();
            }
        } finally {
            releaseWorld();
        }
    }


    /**
     * Acquire the world for thread-safe operations.
     */
    public World acquireWorld() {
        mWorldLock.lock();
        return mWorld;
    }

    /**
     * Release the world after thread-safe operations.
     */
    public void releaseWorld() {
        mWorldLock.unlock();
    }

    /**
     * Acquire the particle system for thread-safe operations.
     * Uses the same lock as LiquidWorld, as all LiquidFun operations should be
     * synchronized. For example, if we are in the middle of LiquidWorld.sync(), we
     * don't want to call ParticleSystem.createParticleGroup() at the same
     * time.
     */
    public ParticleSystem acquireParticleSystem(String key) {
        mWorldLock.lock();
        return ParticleSystems.getInstance().get(key);
    }

    public ParticleSystem acquireParticleSystem() {
        mWorldLock.lock();
        return ParticleSystems.getInstance().get(ParticleSystems.DEFAULT_PARTICLE_SYSTEM);
    }

    /**
     * Release the world after thread-safe operations.
     */
    public void releaseParticleSystem() {
        mWorldLock.unlock();
    }


    void stepWorld(float dt){

        World world = acquireWorld();
        try {
            world.step(
                    dt, VELOCITY_ITERATIONS,
                    POSITION_ITERATIONS, PARTICLE_ITERATIONS);
        } finally {
            releaseWorld();
        }
    }

    void showFrameRate() {
        if (BuildConfig.DEBUG) {
            long time = System.nanoTime();
            if (time - mTime > ONE_SEC) {
                if (totalFrames < 0) {
                    totalFrames = 0;
                    mStartTime = time - 1;
                }
                final float fps = mFrames / ((float) time - mTime) * ONE_SEC;
                float avefps = totalFrames / ((float) time - mStartTime) * ONE_SEC;
                final int count = ParticleSystems.getInstance().getParticleCount();
                Log.d(TAG, fps + " fps (Now)");
                Log.d(TAG, avefps + " fps (Average)");
                Log.d(TAG, count + " particles");
                mTime = time;
                mFrames = 0;

            }
            mFrames++;
            totalFrames++;
        }
    }

    public void createPhysicsObject(float[] vertices){

        World world = acquireWorld();

        try {
           SolidWorld.getInstance().createSolidObject(world, vertices);
        } finally {
            releaseWorld();
        }
    }
}
