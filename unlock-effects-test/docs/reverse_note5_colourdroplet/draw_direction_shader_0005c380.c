
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics28SPDrawColourDropletDirection12createShaderEv(undefined4 param_1)

{
  undefined4 uVar1;
  undefined1 auStack_8a4 [680];
  undefined1 auStack_5fc [1512];
  int iStack_14;
  
  iStack_14 = ___stack_chk_guard;
  func_0x0002801c(auStack_8a4,&UNK_0006f58c,0x2a7);
  uVar1 = func_0x0002801c(auStack_5fc,&UNK_0006f834,0x5e7);
  func_0x00028ea4(param_1,auStack_8a4,uVar1);
  if (iStack_14 == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

