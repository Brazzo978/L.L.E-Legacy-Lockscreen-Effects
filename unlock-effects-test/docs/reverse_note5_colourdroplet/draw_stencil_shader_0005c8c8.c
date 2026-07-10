
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics32SPDrawColourDropletStencilCircle25createPointerSphereShaderEv(undefined4 param_1)

{
  undefined4 uVar1;
  undefined1 auStack_1f4 [112];
  undefined1 auStack_184 [368];
  int iStack_14;
  
  iStack_14 = ___stack_chk_guard;
  func_0x0002801c(auStack_184,&UNK_000704ac,0x16e);
  uVar1 = func_0x0002801c(auStack_1f4,&UNK_0007061c,0x6f);
  func_0x00028ea4(param_1,auStack_184,uVar1);
  if (iStack_14 == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

