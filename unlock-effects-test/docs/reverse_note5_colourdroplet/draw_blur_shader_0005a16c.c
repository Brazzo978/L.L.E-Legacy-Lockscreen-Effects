
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics23SPDrawColourDropletBlur16createBlurShaderEv(undefined4 param_1)

{
  undefined4 uVar1;
  undefined1 auStack_1120 [1212];
  undefined1 auStack_c64 [3152];
  int iStack_14;
  
  iStack_14 = ___stack_chk_guard;
  func_0x0002801c(auStack_1120,&UNK_0006a924,0x4ba);
  uVar1 = func_0x0002801c(auStack_c64,&UNK_0006ade0,0xc4d);
  func_0x00028ea4(param_1,auStack_1120,uVar1);
  if (iStack_14 == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

