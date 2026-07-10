
/* WARNING: Control flow encountered bad instruction data */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

void _ZN8SPhysics26SPDrawColourDropletDensity20createParticleShaderEv(undefined4 param_1)

{
  undefined1 auStack_390 [428];
  undefined1 auStack_1e4 [464];
  int iStack_14;
  
  iStack_14 = ___stack_chk_guard;
  func_0x0002801c(auStack_1e4,&UNK_0006f1e0,0x1ce);
  func_0x0002801c(auStack_390,&UNK_0006f3b0,0x1aa);
  func_0x00028ea4(param_1,auStack_1e4,auStack_390);
  if (iStack_14 == ___stack_chk_guard) {
    return;
  }
  func_0x00028604();
                    /* WARNING: Bad instruction - Truncating control flow here */
  halt_baddata();
}

