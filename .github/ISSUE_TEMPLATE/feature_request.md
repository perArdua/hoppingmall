name: Feature Request
description: 새로운 기능 제안
title: "[feat] "
labels: ["feat"]
body:
  - type: textarea
    id: what
    attributes:
      label: 기능
      placeholder: 어떤 유스케이스/요구사항인지 설명
    validations:
      required: true

  - type: textarea
    id: how
    attributes:
      label: 구현 계획
      placeholder: 대략적인 설계 방향

  - type: input
    id: related
    attributes:
      label: 관련 이슈 번호
      placeholder: "#12"
