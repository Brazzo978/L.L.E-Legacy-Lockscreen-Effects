
void _ZN8SPhysics19SPDrawColourDroplet10drawRenderEv(int param_1)

{
  uint in_fpscr;
  undefined4 uVar1;
  
  func_0x00028e08(param_1,&UNK_00069d40);
  func_0x00028e20(param_1,&UNK_00069d54);
  func_0x00028e14(param_1,&UNK_00069d4c);
  func_0x00029240(param_1,&UNK_0006be5c);
  func_0x000291ec(param_1,&UNK_0006ba5c,param_1 + 0x160,2,1);
  func_0x000291ec(param_1,&UNK_0006be68,param_1 + 0x168,2,1);
  func_0x00029150(param_1,&UNK_0006be78,*(undefined4 *)(param_1 + 0x17c));
  func_0x00029150(param_1,&UNK_0006be84,*(undefined4 *)(param_1 + 0x180));
  func_0x00029150(param_1,&UNK_0006be90,*(undefined4 *)(param_1 + 0x184));
  func_0x00029150(param_1,&UNK_0006bea4,*(undefined4 *)(param_1 + 0x188));
  func_0x00029150(param_1,&UNK_0006beb8,*(undefined4 *)(param_1 + 0x18c));
  func_0x00029150(param_1,&UNK_0006bec8,*(undefined4 *)(param_1 + 0x194));
  func_0x00029150(param_1,&UNK_0006bedc,*(undefined4 *)(param_1 + 0x198));
  func_0x00028e38(param_1,&UNK_0006a920,*(undefined4 *)(param_1 + 0x158));
  func_0x00028e38(param_1,&UNK_0006bef0,*(undefined4 *)(param_1 + 0x154));
  func_0x00028e38(param_1,&UNK_0006befc,*(undefined4 *)(param_1 + 0x15c));
  uVar1 = VectorUnsignedToFloat((uint)*(byte *)(param_1 + 400),(byte)(in_fpscr >> 0x16) & 3);
  func_0x00029150(param_1,&UNK_0006bf10,uVar1);
  _ZN8SPhysics11SPIRenderer20setShaderArrayVectorEPKcPfj
            (param_1,&UNK_0006bf20,*(undefined4 *)(param_1 + 0x170),3);
  return;
}

