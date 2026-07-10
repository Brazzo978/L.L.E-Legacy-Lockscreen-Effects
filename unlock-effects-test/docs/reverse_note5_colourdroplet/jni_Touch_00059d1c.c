
void _ZN16PhysicsEngineJNI12onTouchEventEP7_JNIEnvP7_jclassxiiiP10_jintArrayS5_
               (int *param_1,undefined4 param_2,undefined4 param_3,undefined4 param_4,
               undefined4 param_5,undefined4 param_6,undefined4 param_7,undefined4 param_8,
               undefined4 param_9)

{
  undefined1 auStack_68 [40];
  undefined1 auStack_40 [44];
  
  func_0x00028370(4,&UNK_000698bc,&UNK_0006a64c,&UNK_00070928);
  if (cRam000881c0 == '\0') {
    return;
  }
  (**(code **)(*param_1 + 0x32c))(param_1,param_8,0,10,auStack_68);
  (**(code **)(*param_1 + 0x32c))(param_1,param_9,0,10,auStack_40);
  func_0x00028fdc(param_3,param_5,param_6,param_7,auStack_68,auStack_40);
  return;
}

