
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics25SPDrawColourDropletExport19createDensityShaderEv(undefined4 param_1)

{
  undefined4 uVar1;
  undefined1 auStack_68c [276];
  undefined1 auStack_578 [1380];
  int iStack_14;
  
  iStack_14 = ___stack_chk_guard;
  func_0x0002801c(auStack_68c,&UNK_0006fe24,0x114);
  uVar1 = func_0x0002801c(auStack_578,&UNK_0006ff38,0x561);
  func_0x00028ea4(param_1,auStack_68c,uVar1);
  if (iStack_14 == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

