/**
 * Copyright (c) 2014 Google, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.fpl.liquidfunpaint.renderer;

import com.google.fpl.liquidfun.ParticleGroup;
import com.google.fpl.liquidfun.ParticleSystem;
import com.google.fpl.liquidfunpaint.LiquidWorld;
import com.google.fpl.liquidfunpaint.ParticleSystems;
import com.google.fpl.liquidfunpaint.shader.Material;
import com.google.fpl.liquidfunpaint.shader.ParticleMaterial;
import com.google.fpl.liquidfunpaint.shader.WaterParticleMaterial;
import com.google.fpl.liquidfunpaint.tool.Tool;
import com.google.fpl.liquidfunpaint.util.DrawableResponder;
import com.google.fpl.liquidfunpaint.util.FileHelper;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import org.json.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer to draw particle water, objects, and wall. It draws particles as
 * fluid (or objects) by following three steps:
 * 1) Draws particles to a texture
 * 2) Blurs it out
 * 3) Applies threshold.
 * This only executes on the GLSurfaceView thread.
 */
public class ParticleRenderer implements DrawableResponder {
    private static final String TAG = "PtlRenderer";
    public static final String JSON_FILE = "materials/particlerenderer.json";

    // Framebuffer for the particles to render on.
    public static final int FB_SIZE = 256;

    private WaterParticleMaterial mWaterParticleMaterial;
    private ParticleMaterial mParticleMaterial;
    private BlurRenderer mBlurRenderer;
    private ScreenRenderer mWaterScreenRenderer;
    private ScreenRenderer mScreenRenderer;

    private final RenderSurface[] mRenderSurface = new RenderSurface[2];
    private final float[] mTransformFromTexture = new float[16];
    private final float[] mTransformFromWorld = new float[16];
    private final float[] mPerspectiveTransform = new float[16];

    private ByteBuffer mParticleColorBuffer;
    private ByteBuffer mParticlePositionBuffer;
    private ByteBuffer mParticleWeightBuffer;

    private List<ParticleGroup> mParticleRenderList =
            new ArrayList<ParticleGroup>(256);

    public ParticleRenderer() {
        mParticlePositionBuffer = ByteBuffer
                .allocateDirect(2 * 4 * ParticleSystems.MAX_PARTICLE_COUNT)
                .order(ByteOrder.nativeOrder());
        mParticleColorBuffer = ByteBuffer
                .allocateDirect(4 * ParticleSystems.MAX_PARTICLE_COUNT)
                .order(ByteOrder.nativeOrder());
        mParticleWeightBuffer = ByteBuffer
                .allocateDirect(4 * ParticleSystems.MAX_PARTICLE_COUNT)
                .order(ByteOrder.nativeOrder());
    }

    /**
     * This should only execute on the GLSurfaceView thread.
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        // Per frame resets of buffers
        mParticlePositionBuffer.rewind();
        mParticleColorBuffer.rewind();
        mParticleWeightBuffer.rewind();
        mParticleRenderList.clear();

        ParticleSystem ps = LiquidWorld.getInstance().acquireParticleSystem();
        try {
            int worldParticleCount = ps.getParticleCount();
            // grab the most current particle buffers
            ps.copyPositionBuffer(
                    0, worldParticleCount, mParticlePositionBuffer);
            ps.copyColorBuffer(
                    0, worldParticleCount, mParticleColorBuffer);
            ps.copyWeightBuffer(
                    0, worldParticleCount, mParticleWeightBuffer);

            GLES20.glClearColor(0, 0, 0, 0);

            // Draw the particles
            drawParticles();

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(
                    0, 0,
                    Renderer.getInstance().sScreenWidth,
                    Renderer.getInstance().sScreenHeight);

            // Copy the water particles to screen
            mWaterScreenRenderer.draw(mTransformFromTexture);

            // Copy the other particles to screen
            mScreenRenderer.draw(mTransformFromTexture);
        } finally {
            LiquidWorld.getInstance().releaseParticleSystem();
        }
    }

    private void drawParticles() {
        drawWaterParticles();
        drawNonWaterParticles();
    }

    /**
     * Issue the correct draw call for the ParticleGroup that is passed in.
     */
    private void drawParticleGroup(ParticleGroup pg) {
        // Get the buffer offsets
        int particleCount = pg.getParticleCount();
        int instanceOffset = pg.getBufferIndex();

        // Draw!
        GLES20.glDrawArrays(
                GLES20.GL_POINTS, instanceOffset, particleCount);
    }

    /**
     * Draw all the water particles, and save all the other particle groups
     * into a list. We draw these to temp mRenderSurface[0].
     */
    private void drawWaterParticles() {
        // Draw all water particles to temp render surface 0
        mRenderSurface[0].beginRender(GLES20.GL_COLOR_BUFFER_BIT);

        mWaterParticleMaterial.beginRender();

        // Set attribute arrays
        mWaterParticleMaterial.setVertexAttributeBuffer(
                "aPosition", mParticlePositionBuffer, 0);
        mWaterParticleMaterial.setVertexAttributeBuffer(
                "aColor", mParticleColorBuffer, 0);
        mWaterParticleMaterial.setVertexAttributeBuffer(
                "aWeight", mParticleWeightBuffer, 0);

        // Set uniforms
        GLES20.glUniformMatrix4fv(
                mWaterParticleMaterial.getUniformLocation("uTransform"),
                1, false, mTransformFromWorld, 0);
        GLES20.glUniformMatrix4fv(
                mWaterParticleMaterial.getUniformLocation("uTransformPerspective"),
                1, false, mPerspectiveTransform, 0);

        // Go through each particle group
        ParticleSystem ps = LiquidWorld.getInstance().acquireParticleSystem();
        try {
            ParticleGroup currGroup = ps.getParticleGroupList();

            while (currGroup != null) {
                // Only draw water particles in this pass; queue other groups
                if (currGroup.getGroupFlags() ==
                        Tool.getTool(Tool.ToolType.WATER).getParticleGroupFlags()) {
                    drawParticleGroup(currGroup);
                } else {
                    mParticleRenderList.add(currGroup);
                }

                currGroup = currGroup.getNext();
            }
        } finally {
            LiquidWorld.getInstance().releaseParticleSystem();
        }

        mWaterParticleMaterial.endRender();

        mRenderSurface[0].endRender();

        mBlurRenderer.draw(mRenderSurface[0].getTexture(), mRenderSurface[0]);
    }

    /**
     * Draw all saved ParticleGroups to temp mRenderSurface[1].
     */
    private void drawNonWaterParticles() {
        // Draw all non-water particles to temp render surface 1
        mRenderSurface[1].beginRender(GLES20.GL_COLOR_BUFFER_BIT);

        mParticleMaterial.beginRender();

        // Set attribute arrays
        mParticleMaterial.setVertexAttributeBuffer(
                "aPosition", mParticlePositionBuffer, 0);
        mParticleMaterial.setVertexAttributeBuffer(
                "aColor", mParticleColorBuffer, 0);

        // Set uniforms
        GLES20.glUniformMatrix4fv(
                mParticleMaterial.getUniformLocation("uTransform"),
                1, false, mTransformFromWorld, 0);
        GLES20.glUniformMatrix4fv(
                mParticleMaterial.getUniformLocation("uTransformPerspective"),
                1, false, mPerspectiveTransform, 0);

        ParticleSystem ps = LiquidWorld.getInstance().acquireParticleSystem();
        try {
            // Go through all the particleGroups in the render list
            for (ParticleGroup currGroup : mParticleRenderList) {
                drawParticleGroup(currGroup);
            }
        } finally {
            LiquidWorld.getInstance().releaseParticleSystem();
        }

        mParticleMaterial.endRender();

        mRenderSurface[1].endRender();
        mBlurRenderer.draw(mRenderSurface[1].getTexture(), mRenderSurface[1]);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

        // Set up the transform
        float ratio = (float) height / width;
        Matrix.setIdentityM(mTransformFromTexture, 0);
        Matrix.setIdentityM(mTransformFromWorld, 0);

        if(height > width) // portrait
            resizeTransformPortrait(ratio);
        else // landscape
            resizeTransformLandscape(ratio);

        Matrix.setIdentityM(mPerspectiveTransform, 0);

        float[] mViewMatrix = new float[16];
        float[] mProjectionMatrix = new float[16];
        Matrix.setIdentityM(mViewMatrix, 0);
        Matrix.setIdentityM(mProjectionMatrix, 0);

        Matrix.frustumM(mProjectionMatrix, 0, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 2);
        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0,
                0, 0, -1,
                0f, 0f, 0f,
                0f, 1.0f, 0.0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mPerspectiveTransform, 0, mProjectionMatrix, 0, mViewMatrix, 0);
    }

    private void resizeTransformLandscape(float ratio){
        Matrix.scaleM(mTransformFromTexture, 0, 1, 1 / ratio, 1);

        Matrix.translateM(mTransformFromWorld, 0, -1, -ratio, 0);
        Matrix.scaleM(
                mTransformFromWorld,
                0,
                2 / LiquidWorld.getInstance().sRenderWorldWidth,
                2 *ratio / LiquidWorld.getInstance().sRenderWorldHeight,
                1);
    }

    private void resizeTransformPortrait(float ratio){
        Matrix.scaleM(mTransformFromTexture, 0, 1*ratio, 1, 1);

        Matrix.translateM(mTransformFromWorld, 0, -1/ratio, -1, 0);
        Matrix.scaleM(
                mTransformFromWorld,
                0,
                2 / ratio / LiquidWorld.getInstance().sRenderWorldWidth,
                2 / LiquidWorld.getInstance().sRenderWorldHeight,
                1);
    }

    public void onSurfaceCreated(Context context) {
        // Create the render surfaces
        for (int i = 0; i < mRenderSurface.length; i++) {
            mRenderSurface[i] = new RenderSurface(FB_SIZE, FB_SIZE);
            mRenderSurface[i].setClearColor(Color.argb(0, 255, 255, 255));
        }

        // Create the blur renderer
        mBlurRenderer = new BlurRenderer();

        // Read in our specific json file
        String materialFile = FileHelper.loadAsset(
                context.getAssets(), JSON_FILE);
        try {
            JSONObject json = new JSONObject(materialFile);

            // Water particle material. We are utilizing the position and color
            // buffers returned from LiquidFun directly.
            mWaterParticleMaterial = new WaterParticleMaterial(
                    context, json.getJSONObject("waterParticlePointSprite"));

            // Initialize attributes specific to this material
            mWaterParticleMaterial.addAttribute(
                    "aPosition", 2, Material.AttrComponentType.FLOAT,
                    4, false, 0);
            mWaterParticleMaterial.addAttribute(
                    "aColor", 4, Material.AttrComponentType.UNSIGNED_BYTE,
                    1, true, 0);
            mWaterParticleMaterial.addAttribute(
                    "aWeight", 1, Material.AttrComponentType.FLOAT,
                    1, false, 0);
            mWaterParticleMaterial.setBlendFunc(
                    Material.BlendFactor.ONE,
                    Material.BlendFactor.ONE_MINUS_SRC_ALPHA);

            // Non-water particle material. We are utilizing the position and
            // color buffers returned from LiquidFun directly.
            mParticleMaterial = new ParticleMaterial(
                    context, json.getJSONObject("otherParticlePointSprite"));

            // Initialize attributes specific to this material
            mParticleMaterial.addAttribute(
                    "aPosition", 2, Material.AttrComponentType.FLOAT,
                    4, false, 0);
            mParticleMaterial.addAttribute(
                    "aColor", 4, Material.AttrComponentType.UNSIGNED_BYTE,
                    1, true, 0);
            mParticleMaterial.setBlendFunc(
                    Material.BlendFactor.ONE,
                    Material.BlendFactor.ONE_MINUS_SRC_ALPHA);

            // Scrolling texture when we copy water particles from FBO to screen
            mWaterScreenRenderer = new ScreenRenderer(
                    json.getJSONObject("waterParticleToScreen"),
                    mRenderSurface[0].getTexture());

            // Scrolling texture when we copy water particles from FBO to screen
            mScreenRenderer = new ScreenRenderer(
                    json.getJSONObject("otherParticleToScreen"),
                    mRenderSurface[1].getTexture());

        } catch (JSONException ex) {
            Log.e(TAG, "Cannot parse" + JSON_FILE + "\n" + ex.getMessage());
        }
    }

    public void reset() {
        mParticlePositionBuffer.clear();
        mParticleColorBuffer.clear();
        mParticleWeightBuffer.clear();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

    }

}