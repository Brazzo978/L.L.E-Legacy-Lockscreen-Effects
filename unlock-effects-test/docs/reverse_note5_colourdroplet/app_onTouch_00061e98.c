
void _ZN8SPhysics18SPColourDropletApp12onEventTouchENS_11_TOUCH_TYPEEii
               (int param_1,int param_2,undefined4 param_3,int param_4)

{
  undefined4 *puVar1;
  int iVar2;
  uint in_fpscr;
  undefined8 uVar3;
  undefined4 uStack_30;
  undefined4 uStack_2c;
  undefined4 uStack_28;
  
  iVar2 = *(int *)(param_1 + 0x14);
  if (iVar2 <= param_4) {
    param_4 = iVar2;
  }
  if (param_2 == 1) {
    if (*(char *)(param_1 + 0x9bc) == '\0') {
      return;
    }
    *(undefined1 *)(param_1 + 0x9bc) = 0;
  }
  else if (param_2 == 2) {
    if (*(char *)(param_1 + 0x9bc) == '\0') {
      return;
    }
  }
  else if (param_2 == 0) {
    *(undefined1 *)(param_1 + 0x9bc) = 1;
    uVar3 = FixedToFP(CONCAT44(param_4,param_3),0,0,0,0x20);
    *(undefined8 *)(param_1 + 0x9b4) = uVar3;
  }
  puVar1 = *(undefined4 **)(param_1 + 0x98c);
  if ((uint)(((int)puVar1 - *(int *)(param_1 + 0x990) >> 2) * -0x55555555 +
             ((*(int *)(param_1 + 0x998) - *(int *)(param_1 + 0x988) >> 2) + -1) * 10 +
            (*(int *)(param_1 + 0x984) - *(int *)(param_1 + 0x97c) >> 2) * -0x55555555) < 100) {
    uStack_28 = VectorUnsignedToFloat(param_2,(byte)(in_fpscr >> 0x16) & 3);
    uStack_30 = VectorSignedToFloat(param_3,(byte)(in_fpscr >> 0x16) & 3);
    uStack_2c = VectorSignedToFloat(iVar2 - param_4,(byte)(in_fpscr >> 0x16) & 3);
    if (puVar1 != (undefined4 *)(*(int *)(param_1 + 0x994) + -0xc)) {
      *puVar1 = uStack_30;
      puVar1[1] = uStack_2c;
      puVar1[2] = uStack_28;
      *(undefined4 **)(param_1 + 0x98c) = puVar1 + 3;
      return;
    }
    func_0x000295f4(param_1 + 0x97c,&uStack_30);
  }
  return;
}

