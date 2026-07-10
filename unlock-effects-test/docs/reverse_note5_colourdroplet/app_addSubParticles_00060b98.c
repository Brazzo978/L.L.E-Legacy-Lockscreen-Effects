
void _ZN8SPhysics18SPColourDropletApp15addSubParticlesERKN3glm5tvec2IfLNS1_9precisionE0EEE
               (int param_1,undefined4 *param_2)

{
  undefined4 uVar1;
  uint uVar2;
  uint uVar3;
  uint uVar4;
  uint uVar5;
  uint uVar6;
  uint uVar7;
  uint uVar8;
  uint uVar9;
  uint uVar10;
  uint uVar11;
  uint uVar12;
  uint uVar13;
  undefined4 *puVar14;
  undefined4 *puVar15;
  undefined4 *puVar16;
  int iVar17;
  int iVar18;
  int iVar19;
  uint in_fpscr;
  float fVar20;
  float fVar21;
  float fVar22;
  float fVar23;
  float fVar24;
  uint auStack_38 [3];
  
  uVar1 = func_0x00027d7c(0xb8);
  func_0x00060aa0();
  puVar16 = *(undefined4 **)(param_1 + 0x928);
  if (puVar16 != *(undefined4 **)(param_1 + 0x92c)) {
    *puVar16 = uVar1;
    puVar16 = (undefined4 *)(*(int *)(param_1 + 0x928) + 4);
    *(undefined4 **)(param_1 + 0x928) = puVar16;
    goto code_r0x00060bec;
  }
  iVar19 = *(int *)(param_1 + 0x924);
  iVar17 = (int)puVar16 - iVar19;
  uVar2 = iVar17 >> 2;
  if (uVar2 == 0) {
    uVar3 = 1;
  }
  else {
    uVar3 = uVar2 * 2;
  }
  if ((uVar3 < 0x40000000) && (uVar2 <= uVar3)) {
    if (uVar3 != 0) {
      auStack_38[0] = uVar3 * 4;
      if (0x80 < auStack_38[0]) goto code_r0x00061330;
      puVar14 = (undefined4 *)func_0x00027d94(auStack_38);
      goto code_r0x000612ac;
    }
    puVar14 = (undefined4 *)0x0;
    iVar18 = 0;
  }
  else {
    auStack_38[0] = 0xfffffffc;
code_r0x00061330:
    puVar14 = (undefined4 *)func_0x00027d7c(auStack_38[0]);
code_r0x000612ac:
    iVar19 = *(int *)(param_1 + 0x924);
    iVar18 = (int)puVar14 + (auStack_38[0] & 0xfffffffc);
    iVar17 = (int)puVar16 - iVar19;
  }
  puVar15 = puVar14;
  if (iVar17 != 0) {
    iVar19 = func_0x00027da0(puVar14,iVar19,iVar17);
    puVar15 = (undefined4 *)(iVar19 + iVar17);
  }
  puVar16 = puVar15 + 1;
  *puVar15 = uVar1;
  if (*(int *)(param_1 + 0x924) != 0) {
    if ((uint)((*(int *)(param_1 + 0x92c) - *(int *)(param_1 + 0x924) >> 2) * 4) < 0x81) {
      func_0x00027d1c();
    }
    else {
      func_0x00027d28();
    }
  }
  *(undefined4 **)(param_1 + 0x924) = puVar14;
  *(undefined4 **)(param_1 + 0x928) = puVar16;
  *(int *)(param_1 + 0x92c) = iVar18;
code_r0x00060bec:
  iVar19 = puVar16[-1];
  uVar1 = param_2[1];
  *(undefined4 *)(iVar19 + 0x28) = *param_2;
  *(undefined4 *)(iVar19 + 0x2c) = uVar1;
  uVar1 = param_2[1];
  *(undefined4 *)(iVar19 + 0x20) = *param_2;
  *(undefined4 *)(iVar19 + 0x24) = uVar1;
  fVar24 = *(float *)(param_1 + 0x908) * 0.005;
  do {
    uVar2 = func_0x00027f38();
    uVar3 = func_0x00027f38();
    uVar4 = func_0x00027f38();
    uVar5 = func_0x00027f38();
    uVar6 = func_0x00027f38();
    uVar7 = func_0x00027f38();
    uVar8 = func_0x00027f38();
    uVar9 = func_0x00027f38();
    fVar20 = (float)VectorUnsignedToFloat
                              ((uVar8 & 0xff) % 0xff | (uVar6 & 0xff) % 0xff << 8 |
                               ((uVar4 & 0xff) % 0xff | (uVar2 & 0xff) % 0xff << 8) << 0x10,
                               (byte)(in_fpscr >> 0x16) & 3);
    fVar23 = fVar20 * fVar24 * 4.656613e-10 - fVar24;
    fVar20 = (float)VectorUnsignedToFloat
                              ((uVar9 & 0xff) % 0xff | (uVar7 & 0xff) % 0xff << 8 |
                               ((uVar5 & 0xff) % 0xff | (uVar3 & 0xff) % 0xff << 8) << 0x10,
                               (byte)(in_fpscr >> 0x16) & 3);
    fVar20 = fVar20 * fVar24 * 4.656613e-10 - fVar24;
    in_fpscr = in_fpscr & 0xfffffff |
               (uint)(fVar24 < SQRT(fVar23 * fVar23 + fVar20 * fVar20)) << 0x1f;
  } while (SUB41(in_fpscr >> 0x1f,0));
  fVar22 = *(float *)(param_1 + 0x9b0);
  fVar24 = *(float *)(param_1 + 0x9a8);
  fVar21 = *(float *)(param_1 + 0x1300);
  *(float *)(iVar19 + 0x30) =
       (*(float *)(param_1 + 0x9ac) - *(float *)(param_1 + 0x9a4)) * -0.0001 + fVar23 * fVar21;
  *(float *)(iVar19 + 0x34) = (fVar22 - fVar24) * -0.0001 + fVar20 * fVar21;
  iVar17 = puVar16[-1];
  *(undefined4 *)(iVar17 + 0x6c) = 0;
  uVar2 = func_0x00027f38();
  uVar3 = func_0x00027f38();
  uVar4 = func_0x00027f38();
  uVar5 = func_0x00027f38();
  iVar19 = puVar16[-1];
  fVar24 = (float)VectorUnsignedToFloat
                            ((uVar5 & 0xff) % 0xff | (uVar4 & 0xff) % 0xff << 8 |
                             ((uVar3 & 0xff) % 0xff | (uVar2 & 0xff) % 0xff << 8) << 0x10,
                             (byte)(in_fpscr >> 0x16) & 3);
  *(float *)(iVar17 + 0x60) = fVar24 * 1.1641532e-10 + 0.5;
  *(undefined4 *)(iVar19 + 0x5c) = 0;
  uVar2 = func_0x00027f38();
  uVar3 = func_0x00027f38();
  uVar4 = func_0x00027f38();
  uVar5 = func_0x00027f38();
  iVar17 = puVar16[-1];
  fVar24 = (float)VectorUnsignedToFloat
                            ((uVar5 & 0xff) % 0xff | (uVar4 & 0xff) % 0xff << 8 |
                             ((uVar3 & 0xff) % 0xff | (uVar2 & 0xff) % 0xff << 8) << 0x10,
                             (byte)(in_fpscr >> 0x16) & 3);
  *(float *)(iVar19 + 0x74) = fVar24 * 3.492459e-11 + 0.5;
  uVar2 = func_0x00027f38();
  uVar3 = func_0x00027f38();
  uVar4 = func_0x00027f38();
  uVar5 = func_0x00027f38();
  uVar6 = func_0x00027f38();
  uVar7 = func_0x00027f38();
  uVar8 = func_0x00027f38();
  uVar9 = func_0x00027f38();
  uVar10 = func_0x00027f38();
  uVar11 = func_0x00027f38();
  uVar12 = func_0x00027f38();
  uVar13 = func_0x00027f38();
  fVar20 = (float)VectorUnsignedToFloat
                            ((uVar5 & 0xff) % 0xff | (uVar4 & 0xff) % 0xff << 8 |
                             ((uVar3 & 0xff) % 0xff | (uVar2 & 0xff) % 0xff << 8) << 0x10,
                             (byte)(in_fpscr >> 0x16) & 3);
  fVar24 = (float)VectorUnsignedToFloat
                            ((uVar9 & 0xff) % 0xff | (uVar8 & 0xff) % 0xff << 8 |
                             ((uVar7 & 0xff) % 0xff | (uVar6 & 0xff) % 0xff << 8) << 0x10,
                             (byte)(in_fpscr >> 0x16) & 3);
  fVar23 = (float)VectorUnsignedToFloat
                            ((uVar13 & 0xff) % 0xff | (uVar12 & 0xff) % 0xff << 8 |
                             ((uVar11 & 0xff) % 0xff | (uVar10 & 0xff) % 0xff << 8) << 0x10,
                             (byte)(in_fpscr >> 0x16) & 3);
  *(float *)(iVar17 + 0x90) = fVar20 * 2.3283064e-10;
  *(float *)(iVar17 + 0x94) = fVar24 * 2.3283064e-10;
  *(float *)(iVar17 + 0x98) = fVar23 * 2.3283064e-10;
  return;
}

