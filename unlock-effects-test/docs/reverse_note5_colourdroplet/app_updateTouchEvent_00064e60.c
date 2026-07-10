
/* WARNING: Restarted to delay deadcode elimination for space: stack */

void _ZN8SPhysics18SPColourDropletApp16updateTouchEventEv(int param_1)

{
  int *piVar1;
  float *pfVar3;
  int *piVar4;
  int iVar5;
  int iVar6;
  int iVar7;
  float fVar8;
  float fVar9;
  undefined1 auStack_74 [4];
  undefined4 uStack_70;
  undefined4 uStack_6c;
  int aiStack_68 [2];
  int iStack_60;
  float fStack_58;
  float fStack_54;
  int aiStack_50 [2];
  int iStack_48;
  float fStack_40;
  float fStack_3c;
  undefined1 auStack_38 [20];
  int *piVar2;
  
  fStack_40 = 0.0;
  fStack_3c = 0.0;
  func_0x000297bc(param_1 + 0xe54,&fStack_40);
  pfVar3 = *(float **)(param_1 + 0x97c);
  iVar5 = (*(int *)(param_1 + 0x98c) - *(int *)(param_1 + 0x990) >> 2) * -0x55555555 +
          ((*(int *)(param_1 + 0x998) - *(int *)(param_1 + 0x988) >> 2) + -1) * 10 +
          (*(int *)(param_1 + 0x984) - (int)pfVar3 >> 2) * -0x55555555;
  if (0 < iVar5) {
    iVar6 = 0;
    do {
      while( true ) {
        fVar9 = *pfVar3;
        fVar8 = pfVar3[1];
        iVar7 = (uint)(0.0 < pfVar3[2]) * (int)pfVar3[2];
        if (iVar7 != 1) break;
        *(undefined4 *)(param_1 + 0x2c) = 0;
        if (*(int *)(param_1 + 0x770) == *(int *)(param_1 + 0x774)) goto code_r0x00064f9c;
        func_0x000295dc(&uStack_70,param_1,param_1 + 0x770);
        piVar4 = *(int **)(param_1 + 0x774);
        if (*(int **)(param_1 + 0x770) != piVar4) {
          piVar1 = *(int **)(param_1 + 0x770);
          do {
            piVar2 = piVar1 + 1;
            iVar7 = *piVar1;
            *(undefined4 *)(iVar7 + 0x68) = 0x3f800000;
            *(undefined4 *)(iVar7 + 0x70) = *(undefined4 *)(param_1 + 0x8b0);
            *(undefined4 *)(iVar7 + 0x40) = *(undefined4 *)(iVar7 + 0x5c);
            *(undefined4 *)(iVar7 + 0x44) = *(undefined4 *)(iVar7 + 0x60);
            *(undefined4 *)(iVar7 + 0x20) = uStack_70;
            *(undefined4 *)(iVar7 + 0x24) = uStack_6c;
            piVar1 = piVar2;
          } while (piVar4 != piVar2);
        }
        func_0x0003d8f4(aiStack_68,param_1 + 0x770);
        fStack_58 = 1.4013e-45;
        fStack_54 = 0.0;
        func_0x0003d8f4(aiStack_50,aiStack_68);
        pfVar3 = *(float **)(param_1 + 0x780);
        if (pfVar3 == *(float **)(param_1 + 0x784)) {
          if ((&fStack_58 < *(float **)(param_1 + 0x77c)) || (pfVar3 <= &fStack_58)) {
            func_0x000297d4(param_1 + 0x77c,pfVar3,&fStack_58,&fStack_40,1,1);
          }
          else {
            fStack_40 = fStack_58;
            fStack_3c = fStack_54;
            func_0x0003d8f4(auStack_38,aiStack_50);
            func_0x000297d4(param_1 + 0x77c,pfVar3,&fStack_40,auStack_74,1,1);
            func_0x00033b9c(auStack_38);
          }
        }
        else {
          *pfVar3 = fStack_58;
          pfVar3[1] = fStack_54;
          func_0x0003d8f4(pfVar3 + 2,aiStack_50);
          *(int *)(param_1 + 0x780) = *(int *)(param_1 + 0x780) + 0x14;
        }
        if (aiStack_50[0] != 0) {
          if ((iStack_48 - aiStack_50[0] & 0xfffffffcU) < 0x81) {
            func_0x00027d1c();
          }
          else {
            func_0x00027d28();
          }
        }
        if (aiStack_68[0] != 0) {
          if ((iStack_60 - aiStack_68[0] & 0xfffffffcU) < 0x81) {
            func_0x00027d1c();
          }
          else {
            func_0x00027d28();
          }
        }
        pfVar3 = *(float **)(param_1 + 0x97c);
        if (*(int *)(param_1 + 0x770) != *(int *)(param_1 + 0x774)) {
          *(int *)(param_1 + 0x774) = *(int *)(param_1 + 0x770);
        }
        if (pfVar3 != (float *)(*(int *)(param_1 + 0x984) + -0xc)) goto code_r0x00064fac;
code_r0x00065134:
        if (*(int *)(param_1 + 0x980) != 0) {
          func_0x00027d1c(*(int *)(param_1 + 0x980),0x78);
        }
        iVar7 = *(int *)(param_1 + 0x988);
        iVar6 = iVar6 + 1;
        *(int *)(param_1 + 0x988) = iVar7 + 4;
        pfVar3 = *(float **)(iVar7 + 4);
        *(float **)(param_1 + 0x980) = pfVar3;
        *(float **)(param_1 + 0x97c) = pfVar3;
        *(float **)(param_1 + 0x984) = pfVar3 + 0x1e;
        if (iVar6 == iVar5) goto code_r0x0006517c;
      }
      if (iVar7 == 0) {
        *(float *)(param_1 + 0x9ac) = fVar9;
        *(float *)(param_1 + 0x9b0) = fVar8;
        *(undefined4 *)(param_1 + 0x9a4) = *(undefined4 *)(param_1 + 0x9ac);
        *(undefined4 *)(param_1 + 0x9a8) = *(undefined4 *)(param_1 + 0x9b0);
        *(undefined4 *)(param_1 + 0x9b4) = *(undefined4 *)(param_1 + 0x9ac);
        *(undefined4 *)(param_1 + 0x9b8) = *(undefined4 *)(param_1 + 0x9b0);
        *(undefined4 *)(param_1 + 0x2c) = 1;
      }
      else if (iVar7 == 2) {
        *(float *)(param_1 + 0x9a4) = *(float *)(param_1 + 0x9ac);
        *(undefined4 *)(param_1 + 0x9a8) = *(undefined4 *)(param_1 + 0x9b0);
        *(float *)(param_1 + 0x9ac) = fVar9;
        *(float *)(param_1 + 0x9b0) = fVar8;
        fStack_3c = *(float *)(param_1 + 0x12c0) * (fVar8 - *(float *)(param_1 + 0x9a8));
        fStack_40 = *(float *)(param_1 + 0x12c0) * (fVar9 - *(float *)(param_1 + 0x9a4));
        func_0x000297bc(param_1 + 0xe54,&fStack_40);
        pfVar3 = *(float **)(param_1 + 0x97c);
      }
code_r0x00064f9c:
      if (pfVar3 == (float *)(*(int *)(param_1 + 0x984) + -0xc)) goto code_r0x00065134;
code_r0x00064fac:
      iVar6 = iVar6 + 1;
      pfVar3 = pfVar3 + 3;
      *(float **)(param_1 + 0x97c) = pfVar3;
    } while (iVar6 != iVar5);
  }
code_r0x0006517c:
  func_0x000297c8(param_1,param_1 + 0x9ac);
  return;
}

