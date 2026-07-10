
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics18SPColourDropletApp22addAffordanceParticlesEv(int param_1)

{
  int iVar1;
  int iVar2;
  int iVar3;
  int iStack_110;
  int iStack_10c;
  int iStack_108;
  undefined1 auStack_104 [12];
  undefined4 uStack_f8;
  undefined4 uStack_f4;
  undefined1 auStack_f0 [12];
  undefined4 uStack_e4;
  undefined1 auStack_e0 [100];
  undefined4 uStack_7c;
  undefined1 auStack_38 [12];
  int iStack_2c;
  
  iStack_2c = ___stack_chk_guard;
  func_0x00060aa0(&uStack_e4);
  iStack_110 = 0;
  iStack_108 = 0;
  iStack_10c = 0;
  uStack_e4 = 0x761c0;
  func_0x00033b9c(auStack_38);
  uStack_e4 = 0x76038;
  func_0x0003d214(auStack_e0);
  func_0x00061ff8(&uStack_e4,param_1 + 0x788);
  uStack_7c = 0x3f800000;
  func_0x00029600(auStack_104,param_1 + 0x3e0,&iStack_110);
  uStack_f8 = 0;
  uStack_f4 = 0;
  func_0x0003d8f4(auStack_f0,auStack_104);
  func_0x000617b8(param_1 + 0x77c,&uStack_f8);
  func_0x00033b9c(auStack_f0);
  func_0x00033b9c(auStack_104);
  *(undefined4 *)(param_1 + 0x30) = 1;
  uStack_e4 = 0x761c0;
  func_0x00033b9c(auStack_38);
  uStack_e4 = 0x76038;
  func_0x0003d214(auStack_e0);
  iVar3 = iStack_110;
  iVar1 = iStack_10c;
  iVar2 = iStack_10c;
  while (iVar1 != iVar3) {
    (*(code *)**(undefined4 **)(iVar1 + -0xb8))(iVar1 + -0xb8);
    iVar1 = iVar1 + -0xb8;
    iVar2 = iStack_110;
  }
  if (iVar2 != 0) {
    if ((iStack_108 - iVar2 & 0xfffffff8U) < 0x81) {
      func_0x00027d1c();
    }
    else {
      func_0x00027d28();
    }
  }
  if (iStack_2c == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

