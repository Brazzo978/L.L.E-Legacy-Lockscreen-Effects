
void _ZN8SPhysics18SPColourDropletApp7drawAppEv(int param_1)

{
  int iVar1;
  int iVar2;
  uint in_fpscr;
  undefined4 uVar3;
  undefined4 uVar4;
  float fVar5;
  float fStack_48;
  float fStack_44;
  undefined4 uStack_40;
  float fStack_3c;
  float fStack_38;
  undefined4 uStack_34;
  float fStack_30;
  float fStack_2c;
  undefined4 uStack_28;
  float fStack_24;
  float fStack_20;
  undefined4 uStack_1c;
  
  if (*(char *)(param_1 + 0x22) == '\0') {
    if (*(int *)(param_1 + 0x28) != 0) {
      if (*(int *)(param_1 + 0x28) == 1) {
        if ((*(char *)(param_1 + 0x23) == '\0') && (*(char *)(param_1 + 0x21) == '\0')) {
          func_0x0002975c();
          return;
        }
        func_0x00028370(4,&UNK_000698bc,&UNK_00070764);
        if (*(char *)(param_1 + 0x21) == '\0') {
          uVar3 = func_0x00028e68();
          func_0x00028e74(uVar3,&UNK_000706c4);
        }
        else {
          uVar3 = func_0x00028e68();
          func_0x00028e74(uVar3,&UNK_000706d0);
        }
        func_0x00029738(param_1);
        uVar3 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
        uVar4 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
        *(int *)(param_1 + 0x28) = *(int *)(param_1 + 0x28) + 1;
        func_0x00029534(param_1 + 0xa90,uVar3,uVar4);
        func_0x00029618(param_1);
        func_0x00029534(param_1 + 0x10a4,*(undefined4 *)(param_1 + 0x908),
                        *(undefined4 *)(param_1 + 0x90c));
        func_0x00029534(param_1 + 0xc2c,*(undefined4 *)(param_1 + 0x908),
                        *(undefined4 *)(param_1 + 0x90c));
        func_0x00029534(param_1 + 0xd98,*(undefined4 *)(param_1 + 0x908),
                        *(undefined4 *)(param_1 + 0x90c));
        func_0x00029534(param_1 + 0xe54,*(undefined4 *)(param_1 + 0x908),
                        *(undefined4 *)(param_1 + 0x90c));
      }
      iVar1 = param_1 + 0xa90;
      if (*(char *)(param_1 + 0x1e) != '\0') {
        if (*(char *)(param_1 + 0x1d) != '\0') {
          *(undefined1 *)(param_1 + 0x1d) = 0;
          func_0x0002978c(param_1);
          *(undefined1 *)(param_1 + 0x25) = 1;
        }
        func_0x000296f0(param_1);
        *(undefined1 *)(param_1 + 0x1e) = 0;
      }
      if (*(char *)(param_1 + 0x25) != '\0') {
        iVar2 = param_1 + 0xf30;
        func_0x00029798(iVar2,param_1 + 0x12dc);
        uVar3 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
        uVar4 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
        func_0x00029534(iVar2,uVar3,uVar4);
        func_0x000297a4(iVar2,param_1 + 0x970);
        func_0x000297b0(iVar2,*(undefined4 *)(param_1 + 0x948));
        func_0x00029558(iVar2);
        func_0x00028838(0x8d40,0);
        func_0x00028844(0,0,*(undefined4 *)(param_1 + 0x10),*(undefined4 *)(param_1 + 0x14));
        *(undefined1 *)(param_1 + 0x25) = 0;
      }
      func_0x000296fc(param_1);
      func_0x00029708(param_1);
      fVar5 = *(float *)(param_1 + 0x93c);
      uStack_40 = 0;
      uStack_34 = 0;
      uStack_28 = 0;
      fStack_2c = *(float *)(param_1 + 0x934) + fVar5;
      uStack_1c = 0;
      fStack_48 = *(float *)(param_1 + 0x930) + fVar5;
      fStack_44 = (1.0 - fVar5) + *(float *)(param_1 + 0x934);
      fStack_3c = (1.0 - fVar5) + *(float *)(param_1 + 0x930);
      fStack_38 = fStack_44;
      fStack_30 = fStack_48;
      fStack_24 = fStack_3c;
      fStack_20 = fStack_2c;
      func_0x00029714(iVar1,&fStack_48,&fStack_3c,&fStack_30,&fStack_24);
      fStack_24 = (float)func_0x00029720(*(undefined4 *)(param_1 + 0x12e8));
      func_0x0002972c(iVar1,&fStack_24);
      if (*(char *)(param_1 + 0x24) == '\0') {
        func_0x00029558(iVar1);
        return;
      }
      func_0x00028b2c(0xb90);
      func_0x00029768(1);
      func_0x00029774(0x200,1,1);
      func_0x00029780(0,0x1e00,0x1e00);
      func_0x00029558(param_1 + 0x10a4);
      func_0x00029774(0x205,2,1);
      func_0x00029780(0x1e00,0x1e00,0x1e00);
      func_0x0002975c(param_1);
      func_0x00029774(0x205,1,1);
      func_0x00029558(iVar1);
      func_0x00028b20(0xb90);
      return;
    }
  }
  else {
    func_0x00029738();
    uVar3 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
    uVar4 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
    func_0x00029744(param_1 + 0x9c0,uVar3,uVar4);
    uVar3 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
    uVar4 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
    func_0x00029750(param_1 + 0xa90,uVar3,uVar4);
    func_0x000296f0(param_1);
    *(undefined4 *)(param_1 + 0x28) = 0;
    *(undefined1 *)(param_1 + 0x22) = 0;
  }
  func_0x00028370(4,&UNK_000698bc,&UNK_00070748);
  func_0x0002975c(param_1);
  *(int *)(param_1 + 0x28) = *(int *)(param_1 + 0x28) + 1;
  return;
}

