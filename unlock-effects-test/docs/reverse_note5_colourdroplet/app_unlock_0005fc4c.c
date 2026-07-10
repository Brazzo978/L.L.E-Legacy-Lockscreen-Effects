
void _ZN8SPhysics18SPColourDropletApp6unlockEv(int param_1)

{
  uint in_fpscr;
  float fVar1;
  undefined4 uVar2;
  
  *(undefined1 *)(param_1 + 0x1f) = 1;
  if (*(char *)(param_1 + 0x21) == '\0') {
    uVar2 = *(undefined4 *)(param_1 + 0x14);
  }
  else {
    uVar2 = *(undefined4 *)(param_1 + 0x10);
  }
  fVar1 = (float)VectorSignedToFloat(uVar2,(byte)(in_fpscr >> 0x16) & 3);
  *(bool *)(param_1 + 0x20) = fVar1 * 0.4 < 2.0;
  return;
}

