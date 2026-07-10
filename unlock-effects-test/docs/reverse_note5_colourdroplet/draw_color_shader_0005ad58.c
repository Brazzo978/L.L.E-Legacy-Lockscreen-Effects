
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics24SPDrawColourDropletColor12createShaderEv(undefined4 param_1)

{
  undefined4 uVar1;
  undefined1 auStack_40c [448];
  undefined1 auStack_24c [568];
  int iStack_14;
  
  iStack_14 = ___stack_chk_guard;
  func_0x0002801c(auStack_24c,&UNK_0006ba64,0x235);
  uVar1 = func_0x0002801c(auStack_40c,&UNK_0006bc9c,0x1bf);
  func_0x00028ea4(param_1,auStack_24c,uVar1);
  if (iStack_14 == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

