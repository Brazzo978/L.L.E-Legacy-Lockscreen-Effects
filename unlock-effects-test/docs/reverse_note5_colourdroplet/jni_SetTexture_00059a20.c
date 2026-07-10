
void _ZN16PhysicsEngineJNI10SetTextureEP7_JNIEnvP7_jclassxP8_jstringP8_jobjecth
               (int *param_1,undefined4 param_2,undefined4 param_3,undefined4 param_4,
               undefined4 param_5,undefined4 param_6)

{
  int iVar1;
  undefined4 uVar2;
  undefined4 uVar3;
  undefined4 uStack_30;
  undefined4 uStack_2c;
  undefined4 uStack_28;
  
  func_0x00028370(4,&UNK_000698bc,&UNK_0006a64c,&UNK_00070a38);
  iVar1 = func_0x00029024(param_1,param_6,&uStack_2c);
  if (iVar1 < 0) {
    func_0x00028370(6,&UNK_000698bc,&UNK_0006a5ec);
    return;
  }
  iVar1 = func_0x00029030(param_1,param_6,&uStack_30);
  if (-1 < iVar1) {
    func_0x0002903c(param_1,param_6);
    uVar2 = (**(code **)(*param_1 + 0x2a4))(param_1,param_5,0);
    uVar3 = func_0x00028e68();
    func_0x00029054(uVar3,uVar2,uStack_30,uStack_2c,uStack_28);
    func_0x00028f70(param_3,uVar2);
    (**(code **)(*param_1 + 0x2a8))(param_1,param_5,uVar2);
    return;
  }
  func_0x00028370(6,&UNK_000698bc,&UNK_0006a61c);
  return;
}

