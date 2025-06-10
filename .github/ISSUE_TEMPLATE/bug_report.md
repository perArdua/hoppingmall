name: Bug Report
description: 버그 제보
title: "[fix] "
labels: ["bug"]
body:
  - type: textarea
    id: describe
    attributes:
      label: 발생한 문제
      placeholder: 상세 설명
    validations:
      required: true

  - type: textarea
    id: reproduce
    attributes:
      label: 재현 방법
      placeholder: 어떤 상황에서 문제가 발생했는지 작성
    validations:
      required: true

  - type: input
    id: version
    attributes:
      label: 사용 중인 브랜치/환경
      placeholder: develop, main 등
