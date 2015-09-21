package com.google.fpl.liquidfunpaint;

import android.opengl.GLES20;

import com.google.fpl.liquidfun.ParticleGroup;
import com.google.fpl.liquidfun.ParticleGroupFlag;
import com.google.fpl.liquidfun.ParticleSystem;
import com.google.fpl.liquidfunpaint.shader.ParticleMaterial;
import com.google.fpl.liquidfunpaint.shader.WaterParticleMaterial;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created on 15-09-20.
 */
public class DrawableParticleSystem {

    public final ParticleSystem particleSystem;

    public ByteBuffer mParticleColorBuffer;
    public ByteBuffer mParticlePositionBuffer;
    public ByteBuffer mParticleVelocityBuffer;
    public ByteBuffer mParticleWeightBuffer;

    private List<ParticleGroup> mParticleRenderList =
            new ArrayList<ParticleGroup>(256);

    public DrawableParticleSystem(ParticleSystem pSystem){
        particleSystem = pSystem;

        mParticlePositionBuffer = ByteBuffer
                .allocateDirect(2 * 4 * WorldLock.MAX_PARTICLE_COUNT)
                .order(ByteOrder.nativeOrder());
        mParticleVelocityBuffer = ByteBuffer
                .allocateDirect(2 * 4 * WorldLock.MAX_PARTICLE_COUNT)
                .order(ByteOrder.nativeOrder());
        mParticleColorBuffer = ByteBuffer
                .allocateDirect(4 * WorldLock.MAX_PARTICLE_COUNT)
                .order(ByteOrder.nativeOrder());
        mParticleWeightBuffer = ByteBuffer
                .allocateDirect(4 * WorldLock.MAX_PARTICLE_COUNT)
                .order(ByteOrder.nativeOrder());
    }

    public int getParticleCount(){
        return particleSystem.getParticleCount();
    }

    public void onDrawFrame(){

        mParticleRenderList.clear();

        mParticlePositionBuffer.rewind();
        mParticleColorBuffer.rewind();
        mParticleWeightBuffer.rewind();
        mParticleVelocityBuffer.rewind();

        int worldParticleCount = particleSystem.getParticleCount();
        // grab the most current particle buffers
        particleSystem.copyPositionBuffer(
                0, worldParticleCount, mParticlePositionBuffer);
        particleSystem.copyVelocityBuffer(
                0, worldParticleCount, mParticleVelocityBuffer);
        particleSystem.copyColorBuffer(
                0, worldParticleCount, mParticleColorBuffer);
        particleSystem.copyWeightBuffer(
                0, worldParticleCount, mParticleWeightBuffer);
    }

    public void renderWaterParticles(WaterParticleMaterial mWaterParticleMaterial,
                                     float[] mPerspectiveTransform){

        mWaterParticleMaterial.beginRender();

        // Set attribute arrays
        mWaterParticleMaterial.setVertexAttributeBuffer(
                "aPosition", mParticlePositionBuffer, 0);
        mWaterParticleMaterial.setVertexAttributeBuffer(
                "aVelocity", mParticleVelocityBuffer, 0);
        mWaterParticleMaterial.setVertexAttributeBuffer(
                "aColor", mParticleColorBuffer, 0);
        mWaterParticleMaterial.setVertexAttributeBuffer(
                "aWeight", mParticleWeightBuffer, 0);

        // Set uniforms
        GLES20.glUniformMatrix4fv(
                mWaterParticleMaterial.getUniformLocation("uTransform"),
                1, false, mPerspectiveTransform, 0);

        // Go through each particle group
        ParticleGroup currGroup = particleSystem.getParticleGroupList();

        while (currGroup != null) {
            // Only draw water particles in this pass; queue other groups
            if (currGroup.getGroupFlags() == ParticleGroupFlag.particleGroupCanBeEmpty) {
                drawParticleGroup(currGroup);
            } else {
                mParticleRenderList.add(currGroup);
            }

            currGroup = currGroup.getNext();
        }

        mWaterParticleMaterial.endRender();
    }


    public void renderNonWaterParticles(ParticleMaterial mNonWaterParticleMaterial,
                                        float[] mPerspectiveTransform){

        mNonWaterParticleMaterial.beginRender();

        // Set attribute arrays
        mNonWaterParticleMaterial.setVertexAttributeBuffer(
                "aPosition", mParticlePositionBuffer, 0);
        mNonWaterParticleMaterial.setVertexAttributeBuffer(
                "aColor", mParticleColorBuffer, 0);

        // Set uniforms
        GLES20.glUniformMatrix4fv(
                mNonWaterParticleMaterial.getUniformLocation("uTransform"),
                1, false, mPerspectiveTransform, 0);

        // Go through all the particleGroups in the render list
        for (ParticleGroup currGroup : mParticleRenderList) {
            drawParticleGroup(currGroup);
        }

        mNonWaterParticleMaterial.endRender();
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

    public void reset(){
        mParticlePositionBuffer.clear();
        mParticleColorBuffer.clear();
        mParticleWeightBuffer.clear();
        mParticleVelocityBuffer.clear();
    }

    public void delete(){
        particleSystem.delete();
    }
}
