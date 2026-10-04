package github.kasuminova.mmce.client.util;

import org.lwjgl.util.vector.Quaternion;
import software.bernie.geckolib3.geo.render.built.GeoBone;
import software.bernie.geckolib3.geo.render.built.GeoCube;

import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.util.Arrays;

public class MatrixStack {
    private static final int INITIAL_DEPTH = 8;

    // 下标即栈层级：model[0] 恒为单位矩阵，depth 指向栈顶。push 仅做 set() 值拷贝，不分配。
    private Matrix4f[] model  = new Matrix4f[INITIAL_DEPTH];
    private Matrix3f[] normal = new Matrix3f[INITIAL_DEPTH];
    private int depth = 0;

    private final Matrix4f tempModelMatrix  = new Matrix4f();
    private final Matrix3f tempNormalMatrix = new Matrix3f();

    private final Matrix4f tempRotationModel  = new Matrix4f();
    private final Matrix3f tempRotationNormal = new Matrix3f();

    public MatrixStack() {
        model[0] = new Matrix4f();
        normal[0] = new Matrix3f();

        model[0].setIdentity();
        normal[0].setIdentity();
    }

    @SuppressWarnings({"unused", "NonReproducibleMathCall"})
    private static Quaternion fromAngles(float x, float y, float z) {
        float sx = (float) Math.sin(0.5F * x);
        float cx = (float) Math.cos(0.5F * x);
        float sy = (float) Math.sin(0.5F * y);
        float cy = (float) Math.cos(0.5F * y);
        float sz = (float) Math.sin(0.5F * z);
        float cz = (float) Math.cos(0.5F * z);

        float ox = sx * cy * cz + cx * sy * sz;
        float oy = cx * sy * cz - sx * cy * sz;
        float oz = sx * sy * cz + cx * cy * sz;
        float ow = cx * cy * cz - sx * sy * sz;

        return new Quaternion(ox, oy, oz, ow);
    }

    public Matrix4f getModelMatrix() {
        return this.model[this.depth];
    }

    public Matrix3f getNormalMatrix() {
        return this.normal[this.depth];
    }

    public void push() {
        int next = this.depth + 1;
        if (next >= this.model.length) {
            grow(next + 1);
        }
        if (this.model[next] == null) {
            this.model[next] = new Matrix4f();
            this.normal[next] = new Matrix3f();
        }
        this.model[next].set(this.model[this.depth]);
        this.normal[next].set(this.normal[this.depth]);
        this.depth = next;
    }

    /* Translate */

    public void pop() {
        if (this.depth == 0) {
            throw new IllegalStateException("A one level stack can't be popped!");
        }

        this.depth--;
    }

    private void grow(int required) {
        int newLen = Math.max(required, this.model.length * 2);
        this.model = Arrays.copyOf(this.model, newLen);
        this.normal = Arrays.copyOf(this.normal, newLen);
    }

    public void translate(float x, float y, float z) {
        this.translate(new Vector3f(x, y, z));
    }

    public void translate(Vector3f vec) {
        this.tempModelMatrix.setIdentity();
        this.tempModelMatrix.setTranslation(vec);

        getModelMatrix().mul(this.tempModelMatrix);
    }

    public void moveToPivot(GeoCube cube) {
        Vector3f pivot = cube.pivot;
        this.translate(pivot.getX() / 16, pivot.getY() / 16, pivot.getZ() / 16);
    }

    public void moveBackFromPivot(GeoCube cube) {
        Vector3f pivot = cube.pivot;
        this.translate(-pivot.getX() / 16, -pivot.getY() / 16, -pivot.getZ() / 16);
    }

    public void moveToPivot(GeoBone bone) {
        this.translate(bone.rotationPointX / 16, bone.rotationPointY / 16, bone.rotationPointZ / 16);
    }

    public void moveBackFromPivot(GeoBone bone) {
        this.translate(-bone.rotationPointX / 16, -bone.rotationPointY / 16, -bone.rotationPointZ / 16);
    }

    /* Scale */

    public void translate(GeoBone bone) {
        this.translate(-bone.getPositionX() / 16, bone.getPositionY() / 16, bone.getPositionZ() / 16);
    }

    public void scale(float x, float y, float z) {
        this.tempModelMatrix.setIdentity();
        this.tempModelMatrix.setM00(x);
        this.tempModelMatrix.setM11(y);
        this.tempModelMatrix.setM22(z);

        getModelMatrix().mul(this.tempModelMatrix);

        if (x < 0 || y < 0 || z < 0) {
            this.tempNormalMatrix.setIdentity();
            this.tempNormalMatrix.setM00(x < 0 ? -1 : 1);
            this.tempNormalMatrix.setM11(y < 0 ? -1 : 1);
            this.tempNormalMatrix.setM22(z < 0 ? -1 : 1);

            getNormalMatrix().mul(this.tempNormalMatrix);
        }
    }

    /* Rotate */

    public void scale(GeoBone bone) {
        this.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    public void rotateX(float radian) {
        this.tempModelMatrix.setIdentity();
        this.tempModelMatrix.rotX(radian);

        this.tempNormalMatrix.setIdentity();
        this.tempNormalMatrix.rotX(radian);

        getModelMatrix().mul(this.tempModelMatrix);
        getNormalMatrix().mul(this.tempNormalMatrix);
    }

    public void rotateY(float radian) {
        this.tempModelMatrix.setIdentity();
        this.tempModelMatrix.rotY(radian);

        this.tempNormalMatrix.setIdentity();
        this.tempNormalMatrix.rotY(radian);

        getModelMatrix().mul(this.tempModelMatrix);
        getNormalMatrix().mul(this.tempNormalMatrix);
    }

    public void rotateZ(float radian) {
        this.tempModelMatrix.setIdentity();
        this.tempModelMatrix.rotZ(radian);

        this.tempNormalMatrix.setIdentity();
        this.tempNormalMatrix.rotZ(radian);

        getModelMatrix().mul(this.tempModelMatrix);
        getNormalMatrix().mul(this.tempNormalMatrix);
    }

    public void rotate(GeoBone bone) {
        if (bone.getRotationZ() != 0.0F) {
            this.rotateZ(bone.getRotationZ());
        }

        if (bone.getRotationY() != 0.0F) {
            this.rotateY(bone.getRotationY());
        }

        if (bone.getRotationX() != 0.0F) {
            this.rotateX(bone.getRotationX());
        }
    }

    public void rotate(GeoCube bone) {
        Vector3f rotation = bone.rotation;

        this.tempModelMatrix.setIdentity();
        this.tempRotationModel.rotZ(rotation.getZ());
        this.tempModelMatrix.mul(this.tempRotationModel);

        this.tempRotationModel.rotY(rotation.getY());
        this.tempModelMatrix.mul(this.tempRotationModel);

        this.tempRotationModel.rotX(rotation.getX());
        this.tempModelMatrix.mul(this.tempRotationModel);

        this.tempNormalMatrix.setIdentity();
        this.tempRotationNormal.rotZ(rotation.getZ());
        this.tempNormalMatrix.mul(this.tempRotationNormal);

        this.tempRotationNormal.rotY(rotation.getY());
        this.tempNormalMatrix.mul(this.tempRotationNormal);

        this.tempRotationNormal.rotX(rotation.getX());
        this.tempNormalMatrix.mul(this.tempRotationNormal);

        getModelMatrix().mul(this.tempModelMatrix);
        getNormalMatrix().mul(this.tempNormalMatrix);
    }
}
