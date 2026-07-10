
void _ZN8SPhysics18SPColourDropletApp15addSPHParticlesERKN3glm5tvec2IfLNS1_9precisionE0EEERKj
               (int param_1,undefined4 param_2,undefined4 *param_3)

{
  int iVar1;
  int iVar2;
  int iVar3;
  int iVar4;
  uint uVar5;
  float afStack_58 [2];
  int iStack_50;
  int iStack_4c;
  int iStack_48;
  int iStack_40;
  int iStack_3c;
  int iStack_38;
  int aiStack_30 [2];
  int iStack_28;
  
  aiStack_30[0] = 0;
  iStack_50 = 0;
  iStack_4c = 0;
  iStack_48 = 0;
  func_0x000296d8(param_1 + 0x50,aiStack_30,&iStack_50);
  if ((uint)(iStack_4c - iStack_50) < 0x4b0) {
    iStack_40 = 0;
    iStack_3c = 0;
    iStack_38 = 0;
    afStack_58[0] = *(float *)(param_1 + 0x900) * 0.1;
    func_0x000297f8(param_1 + 0x788,param_2,afStack_58,*param_3,&iStack_40);
    func_0x00029600(aiStack_30,param_1 + 0x50,&iStack_40);
    iVar1 = iStack_40;
    iVar2 = iStack_3c;
    iVar3 = iStack_3c;
    if (aiStack_30[0] != 0) {
      if ((iStack_28 - aiStack_30[0] & 0xfffffffcU) < 0x81) {
        func_0x00027d1c();
        iVar1 = iStack_40;
        iVar2 = iStack_3c;
        iVar3 = iStack_3c;
      }
      else {
        func_0x00027d28();
        iVar1 = iStack_40;
        iVar2 = iStack_3c;
        iVar3 = iStack_3c;
      }
    }
    while (iVar4 = iStack_40, iVar2 != iStack_40) {
      iStack_40 = iVar1;
      (*(code *)**(undefined4 **)(iVar2 + -0xb8))(iVar2 + -0xb8);
      iVar1 = iStack_40;
      iVar2 = iVar2 + -0xb8;
      iVar3 = iStack_40;
      iStack_40 = iVar4;
    }
    iStack_40 = iVar1;
    if (iVar3 != 0) {
      if ((iStack_38 - iVar3 & 0xfffffff8U) < 0x81) {
        func_0x00027d1c();
      }
      else {
        func_0x00027d28();
      }
    }
    if (iStack_50 == 0) {
      return;
    }
    uVar5 = iStack_48 - iStack_50;
  }
  else {
    if (iStack_50 == 0) {
      return;
    }
    uVar5 = iStack_48 - iStack_50;
  }
  if ((uVar5 & 0xfffffffc) < 0x81) {
    func_0x00027d1c();
    return;
  }
  func_0x00027d28(iStack_50);
  return;
}

