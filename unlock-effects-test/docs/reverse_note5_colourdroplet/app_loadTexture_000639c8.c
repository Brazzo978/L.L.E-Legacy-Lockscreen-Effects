
void _ZN8SPhysics18SPColourDropletApp11loadTextureEv(int param_1)

{
  undefined4 uVar1;
  int iVar2;
  uint uVar3;
  int iVar4;
  undefined1 auStack_1c [4];
  undefined4 auStack_18 [3];
  
  func_0x00028370(4,&UNK_000698bc,&UNK_000706f4);
  iVar4 = *(int *)(param_1 + 0x948);
  iVar2 = *(int *)(param_1 + 0x94c);
  auStack_18[0] = 0;
  uVar3 = iVar2 - iVar4 >> 2;
  if (uVar3 < 6) {
    uVar3 = 5 - uVar3;
    if (uVar3 != 0) {
      if ((uint)(*(int *)(param_1 + 0x950) - iVar2 >> 2) < uVar3) {
        func_0x000296b4(param_1 + 0x948,iVar2,auStack_18,auStack_1c,uVar3,0);
        iVar4 = *(int *)(param_1 + 0x948);
      }
      else {
        func_0x000296c0(param_1 + 0x948,iVar2,uVar3,auStack_18,auStack_1c);
        iVar4 = *(int *)(param_1 + 0x948);
      }
    }
  }
  else if (iVar2 != iVar4 + 0x14) {
    *(int *)(param_1 + 0x94c) = iVar4 + 0x14;
  }
  uVar1 = func_0x00028e68();
  uVar1 = func_0x00028e74(uVar1,&UNK_00070708);
  *(undefined4 *)(iVar4 + 4) = uVar1;
  iVar2 = *(int *)(param_1 + 0x948);
  uVar1 = func_0x00028e68();
  uVar1 = func_0x00028e74(uVar1,&UNK_00070710);
  iVar4 = *(int *)(param_1 + 0x948);
  *(undefined4 *)(iVar2 + 8) = uVar1;
  func_0x00029684(param_1 + 0xc2c,iVar4 + 4);
  func_0x00029690(param_1 + 0xa90,*(undefined4 *)(param_1 + 0x948));
  func_0x0002969c(param_1 + 0xd98,*(int *)(param_1 + 0x948) + 4);
  func_0x000296a8(param_1 + 0xe54,*(int *)(param_1 + 0x948) + 4);
  return;
}

