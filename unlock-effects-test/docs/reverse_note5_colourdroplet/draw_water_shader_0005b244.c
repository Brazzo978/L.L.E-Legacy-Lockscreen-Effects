
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics19SPDrawColourDroplet17createWaterShaderEv(undefined4 param_1)

{
  undefined4 uVar1;
  undefined1 auStack_32c4 [2020];
  undefined1 auStack_2ae0 [10956];
  int iStack_14;
  
  iStack_14 = ___stack_chk_guard;
  func_0x0002801c(auStack_32c4,&UNK_0006bf30,0x7e1);
  uVar1 = func_0x0002801c(auStack_2ae0,&UNK_0006c714,0x2ac9);
  func_0x00028ea4(param_1,auStack_32c4,uVar1);
  if (iStack_14 == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

