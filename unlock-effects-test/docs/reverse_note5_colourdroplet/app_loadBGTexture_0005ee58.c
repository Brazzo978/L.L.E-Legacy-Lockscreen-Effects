
void _ZN8SPhysics18SPColourDropletApp13loadBGTextureEv(int param_1)

{
  undefined4 *puVar1;
  uint in_fpscr;
  undefined4 uVar2;
  undefined4 uVar3;
  
  func_0x00028370(4,&UNK_000698bc,&UNK_000706b4);
  uVar2 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
  uVar3 = VectorSignedToFloat(*(undefined4 *)(param_1 + 0x14),(byte)(in_fpscr >> 0x16) & 3);
  func_0x00029534(param_1 + 0x9c0,uVar2,uVar3);
  puVar1 = *(undefined4 **)(param_1 + 0x948);
  if (*(char *)(param_1 + 0x21) == '\0') {
    uVar2 = func_0x00028e68();
    uVar2 = func_0x00028e74(uVar2,&UNK_000706d0);
    *puVar1 = uVar2;
    return;
  }
  uVar2 = func_0x00028e68();
  uVar2 = func_0x00028e74(uVar2,&UNK_000706c4);
  *puVar1 = uVar2;
  return;
}

