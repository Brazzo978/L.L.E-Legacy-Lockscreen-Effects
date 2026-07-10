package android.hardware.scontext;

public class SContextEvent {
    public SContext scontext = new SContext();

    public SContextBounceLongMotion getBounceLongMotionContext() {
        return new SContextBounceLongMotion();
    }
}
