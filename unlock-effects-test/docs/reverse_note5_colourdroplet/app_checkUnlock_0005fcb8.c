
undefined4
_ZN8SPhysics18SPColourDropletApp11checkUnlockERKN3glm5tvec2IfLNS1_9precisionE0EEE
          (int param_1,float *param_2)

{
  uint in_fpscr;
  float fVar1;
  float fVar2;
  float fVar3;
  
  fVar2 = *(float *)(param_1 + 0x9b4) - *param_2;
  fVar3 = *(float *)(param_1 + 0x9b8) - param_2[1];
  fVar1 = (float)VectorSignedToFloat(*(undefined4 *)(param_1 + 0x10),(byte)(in_fpscr >> 0x16) & 3);
  if (SQRT(fVar2 * fVar2 + fVar3 * fVar3) <= fVar1 * 0.75) {
    return 0;
  }
  func_0x000295d0();
  return 1;
}

